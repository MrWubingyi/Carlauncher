param([string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot))

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$violations = [System.Collections.Generic.List[string]]::new()

function Assert-NoDependency([string]$RelativePath, [string]$Pattern) {
    $path = Join-Path $root $RelativePath
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing layer: $RelativePath" }
    $files = @(Get-ChildItem -LiteralPath $path -Recurse -File | Where-Object {
        $_.Extension -in @('.kt', '.java', '.c', '.h', '.hpp', '.cpp')
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
Assert-NoDependency 'someip-android/src/main/cpp/native' '(?m)^\s*#include.*(jni\.h|carlauncher|VehicleSomeIpConfig|VehicleProp|aidl/)'
Assert-NoDependency 'someip-android/src/main/cpp/jni' '(vsomeip::|register_message_handler|request_service\(|subscribe\()'
Assert-NoDependency 'someip-android/src/main' '(com\.example\.(carlauncher|vehicle)|VehicleSomeIpConfig|SomeipPayloadCodec|soc-vehicle-client|0x1111|0x2222)'
Assert-NoDependency 'app/src/main/java' '(import com\.example\.someip\.internal\.|System\.loadLibrary\("(vsomeip3|someip_jni|carlauncher)"\))'
Assert-NoDependency 'app/src/main/java/com/example/vehicle/someip/android' 'external fun'
Assert-NoDependency 'app/src/main/java/com/example/vehicle/someip/android' '(SomeIpService\(|soc-vehicle-client|0x1111|0x2222)'
Assert-NoDependency 'app/src/main/java/com/example/vehicle' '(?m)^import com\.example\.carlauncher\.'
Assert-NoDependency 'app/src/main/java/com/example/vehicle/model' '(?m)^import (android\.|androidx\.|com\.example\.vehicle\.someip\.)'
Assert-NoDependency 'app/src/main/java/com/example/vehicle/someip/codec' '(?m)^import (android\.|androidx\.|com\.example\.vehicle\.someip\.(adapter|transport|android)\.)'
Assert-NoDependency 'app/src/main/java/com/example/vehicle/someip/adapter' '(?m)^import (android\.|androidx\.|com\.example\.vehicle\.someip\.android\.)'
Assert-NoDependency 'app/src/main/java/com/example/vehicle/someip/transport/NativeVehicleTransport.kt' '(?m)^import (android\.|androidx\.|com\.example\.carlauncher\.(model|data)\.)'
Assert-NoDependency 'app/src/main/java/com/example/vehicle/someip/adapter/SomeIpVehicleAdapter.kt' '(?m)^import (android\.|androidx\.|com\.example\.carlauncher\.(model|data)\.)'
Assert-NoDependency 'app/src/main/java/com/example/vehicle/someip/android/VSomeIpNativeTransport.kt' '(SomeIpEventCodec|VehicleEventCodec|VehicleSignalMapper|import .*\.VehicleState|NativeBridge|VsomeipClient)'
Assert-NoDependency 'app/src/main/java/com/example/vehicle/someip/android' '(?m)^import com\.example\.vehicle\.someip\.adapter\.'
Assert-NoDependency 'app/src/main/java/com/example/carlauncher/data/someip/VSomeIpDataSource.kt' '(JSONObject|SomeIpEventCodec|VehicleEventCodec|VsomeipClient\.)'

# Only these integration files may reference the library, and only its listed public API.
# Scan all production source sets (including debug/release/flavors), not just main/java.
$publicApiUsers = @{
    'app/src/main/java/com/example/vehicle/someip/android/VehicleSomeIpClientFactory.kt' = @('SomeIpClient', 'VSomeIp')
    'app/src/main/java/com/example/vehicle/someip/android/VSomeIpNativeTransport.kt' = @('SomeIpClient')
    'app/src/main/java/com/example/vehicle/someip/android/SomeIpConfigPreparer.kt' = @('SomeIpClientConfig')
}
$productionSets = Get-ChildItem -LiteralPath (Join-Path $root 'app/src') -Directory |
    Where-Object { $_.Name -notmatch '^(test|androidTest)' }
foreach ($sourceSet in $productionSets) {
    $files = Get-ChildItem -LiteralPath $sourceSet.FullName -Recurse -File
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($root.Length + 1).Replace('\', '/')
        if ($file.Name -match '^lib(vsomeip.*|someip_jni)\.so$') {
            $violations.Add("${relative}: SOME/IP native artifacts must be packaged by :someip-android")
        }
        if ($file.Extension -notin @('.kt', '.java', '.c', '.h', '.hpp', '.cpp')) { continue }
        $content = Get-Content -LiteralPath $file.FullName -Raw
        if ($relative -match '/com/example/carlauncher/service/' -and
            $content -match '(VSomeIpNativeTransport|VSomeIpDataSource|NativeVehicleTransport|SomeIpVehicleEventSourceFactory|com\.example\.vehicle\.someip\.android|com\.example\.carlauncher\.data\.someip\.)') {
            $violations.Add("${relative}: Service must use the injected factory and VehicleDataSource interfaces")
        }
        foreach ($reference in [regex]::Matches($content, 'com\.example\.someip\.([\w*]+)')) {
            if ($reference.Groups[1].Value -notin $publicApiUsers[$relative]) {
                $violations.Add("${relative}: unauthorized library reference $($reference.Value)")
            }
        }
        # Includes fully qualified calls, reflection strings, native headers and direct JNI access.
        if ($content -match '(\bNativeBridge\b|\bVsomeipClient\b|vsomeip::|vsomeip/|\bVSOMEIP_(CONFIGURATION|APPLICATION_NAME|BASE_PATH)\b|["''](?:lib)?(?:vsomeip3|someip_jni)(?:\.so)?["''])') {
            $violations.Add("${relative}: direct vSomeIP implementation dependency")
        }
    }
}

$appBuild = Get-Content -LiteralPath (Join-Path $root 'app/build.gradle.kts') -Raw
if ($appBuild -match '(vsomeip3|someip_jni|third_party[/\\]vsomeip)') {
    $violations.Add('app must consume :someip-android instead of linking vSomeIP artifacts directly')
}

$libraryBuild = Get-Content -LiteralPath (Join-Path $root 'someip-android/build.gradle.kts') -Raw
if ($libraryBuild -match 'project\s*\(\s*"\:app"') {
    $violations.Add('someip-android must not depend on :app')
}

if ($violations.Count -gt 0) {
    $violations | ForEach-Object { Write-Output $_ }
    throw 'SOME/IP layer dependency check failed.'
}
Write-Output '[OK] SOME/IP layer dependencies'
