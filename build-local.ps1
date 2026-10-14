<#
.SYNOPSIS
Build a Debug APK, preparing its Android vSomeIP library on Ubuntu when necessary.
.EXAMPLE
.\build-local.ps1 -UbuntuHost root@192.168.112.129 -SaveConfiguration
.EXAMPLE
.\build-local.ps1
#>
[CmdletBinding()]
param(
    [string]$UbuntuHost = $env:CARLAUNCHER_UBUNTU_HOST,
    [string]$RemoteRoot,
    [string]$RemoteSdk,
    [string]$JavaHome,
    [ValidateRange(1, 32)][int]$Jobs = 2,
    [switch]$SaveConfiguration,
    [switch]$RebuildNative,
    [switch]$Test
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = $PSScriptRoot
$configPath = Join-Path $root 'local-build.json'
$tools = Join-Path $root 'tools/vsomeip-android'
$oldJavaHome = $env:JAVA_HOME
$buildLock = $null

function Run([string]$Command, [string[]]$Arguments) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command failed (exit $LASTEXITCODE)." }
}
function Capture([string]$Command, [string[]]$Arguments) {
    $output = & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Command failed (exit $LASTEXITCODE)." }
    return ($output -join "`n").Trim()
}
function Require-Tool([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) { throw "Missing local prerequisite: $Name" }
}
function Write-Utf8([string]$Path, [string]$Content) {
    [IO.File]::WriteAllText($Path, $Content.Replace("`r`n", "`n"), [Text.UTF8Encoding]::new($false))
}
function Full-Jdk21([string]$Path) {
    if (-not $Path -or -not (Test-Path "$Path/release")) { return $false }
    return ((Get-Content "$Path/release" -Raw) -match 'JAVA_VERSION="21[.+"]' -and
        (Test-Path "$Path/bin/java.exe") -and (Test-Path "$Path/bin/javac.exe") -and
        (Test-Path "$Path/bin/jlink.exe"))
}
function Assert-WorkspacePath([string]$Path) {
    $absolute = [IO.Path]::GetFullPath($Path)
    $prefix = [IO.Path]::GetFullPath($root).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    if (-not $absolute.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Generated path is outside the workspace: $absolute"
    }
}

Push-Location $root
try {
    New-Item -ItemType Directory -Path (Join-Path $root 'build') -Force | Out-Null
    try {
        $buildLock = [IO.File]::Open((Join-Path $root 'build/local-build.lock'),
            [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    } catch { throw 'Another local build is running, or build/local-build.lock is inaccessible.' }
    if (Test-Path $configPath) {
        $config = Get-Content $configPath -Raw | ConvertFrom-Json
        foreach ($name in @('UbuntuHost', 'RemoteRoot', 'RemoteSdk', 'JavaHome')) {
            if (-not (Get-Variable $name -ValueOnly) -and $config.PSObject.Properties[$name]) {
                Set-Variable $name $config.$name
            }
        }
    }
    foreach ($tool in @('git', 'python')) { Require-Tool $tool }
    Run 'python' @('-c', 'import sys; assert sys.version_info >= (3, 9), "Python 3.9+ required"')
    $commit = Capture 'git' @('rev-parse', 'HEAD')
    $gitlink = Capture 'git' @('ls-tree', 'HEAD', 'third_party/vsomeip')
    if ($gitlink -notmatch '^160000 commit ([0-9a-f]{40})\s') { throw 'Missing committed vSomeIP gitlink.' }
    $sourceCommit = $Matches[1]
    if (-not (Test-Path 'third_party/vsomeip/interface/vsomeip/vsomeip.hpp')) {
        Run 'git' @('submodule', 'update', '--init', '--recursive')
    }
    if ((Capture 'git' @('-C', 'third_party/vsomeip', 'rev-parse', 'HEAD')) -ne $sourceCommit) {
        throw 'vSomeIP checkout differs from HEAD gitlink. Commit the intended gitlink first.'
    }
    $changes = Capture 'git' @('-C', 'third_party/vsomeip', 'status', '--porcelain', '--untracked-files=all', '--', '.', ':(exclude)documentation/**')
    if ($changes) { throw "Uncommitted vSomeIP build inputs; commit them first:`n$changes" }

    if (-not $JavaHome) {
        $candidates = @($env:JAVA_HOME, "$env:ProgramFiles/Android/Android Studio/jbr")
        if (Test-Path "$env:USERPROFILE/.jdks") {
            $candidates += @(Get-ChildItem "$env:USERPROFILE/.jdks" -Directory | Select-Object -ExpandProperty FullName)
        }
        $JavaHome = $candidates | Where-Object { Full-Jdk21 $_ } | Select-Object -First 1
    }
    if (-not (Full-Jdk21 $JavaHome)) { throw 'A complete JDK 21 (java, javac, jlink) is required. Set JAVA_HOME or pass -JavaHome.' }
    $JavaHome = (Resolve-Path $JavaHome).Path
    $env:JAVA_HOME = $JavaHome
    Write-Host "Using complete JDK 21: $JavaHome"

    $sdk = $env:ANDROID_HOME
    if (Test-Path 'local.properties') {
        $sdkLine = Get-Content 'local.properties' | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine) { $sdk = $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\') }
    }
    if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
    if (-not $sdk) { throw 'Configure sdk.dir in local.properties or set ANDROID_HOME.' }
    $sdkFiles = @('platforms/android-36.1/optional/android.car.jar', 'build-tools/36.0.0/aapt2.exe',
        'ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe', 'cmake/3.22.1/bin/cmake.exe')
    if (@($sdkFiles | Where-Object { -not (Test-Path (Join-Path $sdk $_)) }).Count) {
        Require-Tool 'android'
        Run 'android' @('--no-metrics', "--sdk=$sdk", 'sdk', 'install', 'platforms/android-36.1',
            'build-tools/36.0.0', 'ndk/28.2.13676358', 'cmake/3.22.1')
        foreach ($file in $sdkFiles) { if (-not (Test-Path (Join-Path $sdk $file))) { throw "SDK component missing: $file" } }
    }

    Write-Host 'Running Detekt before preparing native dependencies...'
    Run "$root/gradlew.bat" @('--no-daemon', '-Porg.gradle.java.installations.auto-detect=false',
        "-Porg.gradle.java.installations.paths=$JavaHome", ':app:detekt')

    # A different repository commit or native script invalidates the cache.
    $inputText = "$commit`n$sourceCommit`n"
    foreach ($file in @('build-source.sh', 'verify-artifact.py', 'remote-build.sh')) {
        $inputText += "$file`n" + [IO.File]::ReadAllText((Join-Path $tools $file)).Replace("`r`n", "`n") + "`n"
    }
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { $inputHash = ([BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes($inputText)))).Replace('-', '').ToLowerInvariant() }
    finally { $hasher.Dispose() }
    $artifact = Join-Path $root 'build/native/artifact'
    $verifyArgs = @("$tools/verify-artifact.py", '--validate', '--artifact', $artifact,
        '--repository-commit', $commit, '--vsomeip-commit', $sourceCommit, '--input-hash', $inputHash)
    $valid = $false
    if ((Test-Path "$artifact/SHA256SUMS") -and -not $RebuildNative) {
        & python @verifyArgs
        $valid = $LASTEXITCODE -eq 0
    }
    if (-not $valid) {
        if (-not $UbuntuHost) { throw 'Native dependency needs rebuilding. Supply -UbuntuHost user@host -SaveConfiguration once, or set CARLAUNCHER_UBUNTU_HOST.' }
        # Restrict values sent through the SSH shell to simple paths and host names.
        if ($UbuntuHost -notmatch '^[A-Za-z0-9_][A-Za-z0-9_.@-]*$') { throw 'UbuntuHost must be an SSH alias or user@hostname.' }
        foreach ($tool in @('ssh', 'scp')) { Require-Tool $tool }
        $sshOptions = @('-o', 'BatchMode=yes', '-o', 'ConnectTimeout=15')
        if (-not $RemoteRoot) {
            $remoteHome = Capture 'ssh' ($sshOptions + @($UbuntuHost, 'printenv HOME'))
            $RemoteRoot = "$remoteHome/.cache/carlauncher-local-build"
        }
        if (-not $RemoteSdk) { $RemoteSdk = "$RemoteRoot/sdk" }
        foreach ($path in @($RemoteRoot, $RemoteSdk)) {
            if ($path -notmatch '^/[A-Za-z0-9_./-]+$' -or $path -match '(^|/)\.\.(/|$)') {
                throw 'Remote paths must be absolute and contain only letters, numbers, _, -, . and /; no .. segments.'
            }
        }
        $runId = [Guid]::NewGuid().ToString('N')
        $transfer = Join-Path $root "build/local-build/$runId"
        New-Item -ItemType Directory -Path $transfer -Force | Out-Null
        $upload = "$RemoteRoot/runs/$runId"
        Run 'ssh' ($sshOptions + @($UbuntuHost, "mkdir -p $upload"))
        Write-Host 'Packaging pinned source commits (no remote Git credentials required)...'
        Run 'git' @('bundle', 'create', "$transfer/main.bundle", 'HEAD')
        Run 'git' @('-C', 'third_party/vsomeip', 'bundle', 'create', "$transfer/vsomeip.bundle", 'HEAD')
        foreach ($file in @('build-source.sh', 'verify-artifact.py', 'remote-build.sh')) {
            Write-Utf8 "$transfer/$file" ([IO.File]::ReadAllText((Join-Path $tools $file)))
        }
        Run 'scp' ($sshOptions + @("$transfer/main.bundle", "$transfer/vsomeip.bundle", "$transfer/build-source.sh",
            "$transfer/verify-artifact.py", "$transfer/remote-build.sh", "${UbuntuHost}:$upload/"))
        $force = if ($RebuildNative) { '1' } else { '0' }
        Write-Host "Building native dependency on $UbuntuHost; log: $upload/console.log"
        # pipefail preserves failures even though tee streams a durable log.
        $remoteCommand = "bash -o pipefail -c 'bash $upload/remote-build.sh $RemoteRoot $upload $RemoteSdk $commit $sourceCommit $inputHash $Jobs $force 2>&1 | tee $upload/console.log'"
        try { Run 'ssh' ($sshOptions + @($UbuntuHost, $remoteCommand)) }
        finally {
            & scp @sshOptions "${UbuntuHost}:$upload/console.log" "$transfer/console.log"
        }
        Run 'scp' ($sshOptions + @("${UbuntuHost}:$upload/native-artifact.tar.gz", "$transfer/native-artifact.tar.gz"))
        $stage = Join-Path $transfer 'artifact'
        New-Item -ItemType Directory -Path $stage | Out-Null
        Run 'python' @("$tools/verify-artifact.py", '--validate', '--artifact', $stage,
            '--extract', "$transfer/native-artifact.tar.gz",
            '--repository-commit', $commit, '--vsomeip-commit', $sourceCommit, '--input-hash', $inputHash)
        New-Item -ItemType Directory -Path (Split-Path $artifact) -Force | Out-Null
        foreach ($path in @($artifact, $stage, "$transfer/previous-artifact")) { Assert-WorkspacePath $path }
        if (Test-Path $artifact) { Move-Item -LiteralPath $artifact -Destination "$transfer/previous-artifact" }
        Move-Item -LiteralPath $stage -Destination $artifact
    } else { Write-Host 'Reusing verified local native dependency; Ubuntu connection is unnecessary.' }
    if ($SaveConfiguration) {
        Write-Utf8 $configPath (([ordered]@{ UbuntuHost = $UbuntuHost; RemoteRoot = $RemoteRoot;
            RemoteSdk = $RemoteSdk; JavaHome = $JavaHome } | ConvertTo-Json) + "`n")
        Write-Host "Saved machine settings to $configPath (ignored by Git)."
    }
    $tasks = @(':app:assembleDebug')
    if ($Test) { $tasks += ':app:testDebugUnitTest' }
    Run "$root/gradlew.bat" (@('--no-daemon', '-Porg.gradle.java.installations.auto-detect=false',
        "-Porg.gradle.java.installations.paths=$JavaHome") + $tasks)
    $apk = Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk'
    Run 'python' @("$tools/verify-artifact.py", '--apk', $apk, '--artifact', $artifact)
    Write-Host "Build successful: $apk"
} finally {
    if ($null -ne $buildLock) { $buildLock.Dispose() }
    $env:JAVA_HOME = $oldJavaHome
    Pop-Location
}
