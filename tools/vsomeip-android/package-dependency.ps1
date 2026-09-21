param(
    [string]$SourceRoot = (Join-Path $PSScriptRoot '../../third_party/vsomeip'),
    [string]$Library = (Join-Path $SourceRoot 'build-android-x86_64-mono/libvsomeip3.so'),
    [string]$NdkRoot = 'D:\Android\SDK\ndk\28.2.13676358',
    [string]$BoostLicense = 'E:\Src\vcpkg\installed\x64-android24\share\boost-headers\copyright'
)

$ErrorActionPreference = 'Stop'
$destination = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../third_party/vsomeip-android'))
$strip = Join-Path $NdkRoot 'toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe'
$nm = Join-Path $NdkRoot 'toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-nm.exe'
$sourceLicense = Join-Path $SourceRoot 'LICENSE'
$connector = Join-Path $SourceRoot 'implementation/endpoints/src/android_network_connector.inc'
foreach ($required in @($Library, $strip, $nm, $sourceLicense, $BoostLicense, $connector,
        (Join-Path $SourceRoot 'interface/vsomeip/vsomeip.hpp'))) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Required input missing: $required"
    }
}
$revision = & git -C $SourceRoot rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $revision -notmatch '^[0-9a-f]{40}$') {
    throw 'The source must be an initialized Git checkout.'
}
$sourceChanges = & git -C $SourceRoot status --porcelain --untracked-files=all -- CMakeLists.txt interface implementation
if ($LASTEXITCODE -ne 0 -or $sourceChanges) { throw 'Commit source changes before packaging the matching library.' }
$symbols = & $nm -D --defined-only $Library
if ($LASTEXITCODE -ne 0 -or -not ($symbols -match ' T vsomeip_android_set_network_state$')) {
    throw 'The library must export the Android network adapter.'
}

New-Item -ItemType Directory -Force -Path "$destination/lib/x86_64", "$destination/licenses" | Out-Null
Copy-Item -LiteralPath $sourceLicense -Destination "$destination/licenses/vsomeip-MPL-2.0.txt" -Force
Copy-Item -LiteralPath $BoostLicense -Destination "$destination/licenses/boost.txt" -Force
& $strip --strip-unneeded -o "$destination/lib/x86_64/libvsomeip3.so" $Library
if ($LASTEXITCODE -ne 0) { throw 'Stripping the library failed.' }

# Source and headers are managed by the submodule, not copied into this package.
[IO.File]::WriteAllText("$destination/SOURCE_COMMIT", ($revision + "`n"), [Text.UTF8Encoding]::new($false))

$inputs = @(
    Get-ChildItem -LiteralPath "$destination/licenses", "$destination/lib" -Recurse -File
    Get-Item -LiteralPath "$destination/SOURCE_COMMIT"
)
$checksums = foreach ($file in ($inputs | Sort-Object FullName)) {
    $relative = $file.FullName.Substring($destination.Length + 1).Replace('\', '/')
    $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $relative"
}
[IO.File]::WriteAllText("$destination/SHA256SUMS", (($checksums -join "`n") + "`n"), [Text.UTF8Encoding]::new($false))
Write-Output 'Packaged the patched x86_64 library, source revision, licenses and checksums.'
