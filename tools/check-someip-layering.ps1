param([string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot))

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$violations = [System.Collections.Generic.List[string]]::new()

function Assert-NoDependency([string]$RelativePath, [string]$Pattern) {
    $path = Join-Path $root $RelativePath
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing layer: $RelativePath" }
    $files = @(Get-ChildItem -LiteralPath $path -Recurse -File | Where-Object {
        $_.Extension -in @('.kt', '.h', '.cpp')
    })
    if ($files.Count -eq 0) { throw "Empty layer: $RelativePath" }
    foreach ($file in $files) {
        $matches = @(Select-String -LiteralPath $file.FullName -Pattern $Pattern)
        foreach ($match in $matches) {
            $violations.Add(('{0}:{1}: {2}' -f $file.FullName, $match.LineNumber, $match.Line.Trim()))
        }
    }
}

# Lightweight source dependency guard; compilation/tests remain separate checks.
Assert-NoDependency 'vehicle/native' '(?m)^\s*#include.*(jni\.h|carlauncher|VehicleSomeIpConfig|VehicleProp|aidl/)'
Assert-NoDependency 'app/src/main/cpp/jni/VsomeipJni.cpp' '(vsomeip::|register_message_handler|request_service\(|subscribe\()'
Assert-NoDependency 'app/src/main/java/com/example/vehicle' '(?m)^import com\.example\.carlauncher\.'
Assert-NoDependency 'app/src/main/java/com/example/vehicle/model' '(?m)^import (android\.|androidx\.|com\.example\.vehicle\.someip\.)'
Assert-NoDependency 'app/src/main/java/com/example/vehicle/someip/codec' '(?m)^import (android\.|androidx\.|com\.example\.vehicle\.someip\.(adapter|transport|android)\.)'
Assert-NoDependency 'app/src/main/java/com/example/vehicle/someip/adapter' '(?m)^import (android\.|androidx\.|com\.example\.vehicle\.someip\.android\.)'
Assert-NoDependency 'app/src/main/java/com/example/vehicle/someip/transport/NativeVehicleTransport.kt' '(?m)^import (android\.|androidx\.|com\.example\.carlauncher\.(model|data)\.)'
Assert-NoDependency 'app/src/main/java/com/example/vehicle/someip/adapter/SomeIpVehicleAdapter.kt' '(?m)^import (android\.|androidx\.|com\.example\.carlauncher\.(model|data)\.)'
Assert-NoDependency 'app/src/main/java/com/example/vehicle/someip/android/VSomeIpNativeTransport.kt' '(SomeIpEventCodec|VehicleEventCodec|VehicleSignalMapper|import .*\.VehicleState)'
Assert-NoDependency 'app/src/main/java/com/example/vehicle/someip/android' '(?m)^import com\.example\.vehicle\.someip\.adapter\.'
Assert-NoDependency 'app/src/main/java/com/example/carlauncher/data/someip/VSomeIpDataSource.kt' '(JSONObject|SomeIpEventCodec|VehicleEventCodec|VsomeipClient\.)'

if ($violations.Count -gt 0) {
    $violations | ForEach-Object { Write-Output $_ }
    throw 'SOME/IP layer dependency check failed.'
}
Write-Output '[OK] SOME/IP layer dependencies'
