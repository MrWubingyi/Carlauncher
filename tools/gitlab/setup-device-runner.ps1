param(
    [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$JavaHome,
    [string]$Serial = 'emulator-5554'
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$local = Join-Path $root '.gitlab-local'
$runnerDirectory = Join-Path $local 'device-runner'
New-Item -ItemType Directory -Force $runnerDirectory | Out-Null
if (-not $JavaHome) {
    $JavaHome = (Get-Content (Join-Path $root 'local-build.json') -Raw | ConvertFrom-Json).JavaHome
}
foreach ($file in @("$Sdk/platform-tools/adb.exe", "$Sdk/ndk/28.2.13676358/simpleperf/app_profiler.py", "$JavaHome/bin/java.exe")) {
    if (-not (Test-Path -LiteralPath $file)) { throw "Missing prerequisite: $file" }
}
$exe = Join-Path $runnerDirectory 'gitlab-runner.exe'
$version = '19.4.1'
if (-not (Test-Path -LiteralPath $exe)) {
    Invoke-WebRequest "https://gitlab-runner-downloads.s3.amazonaws.com/v$version/binaries/gitlab-runner-windows-amd64.exe" -OutFile $exe
}
$manifest = Join-Path $runnerDirectory 'release.sha256'
if (-not (Test-Path -LiteralPath $manifest)) {
    Invoke-WebRequest "https://gitlab-runner-downloads.s3.amazonaws.com/v$version/release.sha256" -OutFile $manifest
}
$entry = Get-Content $manifest | Where-Object { $_ -match '\s+binaries/gitlab-runner-windows-amd64.exe$' }
if (-not $entry) { throw 'Official runner checksum entry missing' }
$expected = ($entry -split '\s+')[0]
if ((Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash -ne $expected) { throw 'Runner checksum mismatch' }
$bootstrap = Get-Content (Join-Path $local 'bootstrap.json') -Raw | ConvertFrom-Json
$headers = @{ 'PRIVATE-TOKEN' = $bootstrap.api_token }
$registrationFile = Join-Path $runnerDirectory 'registration.json'
if (Test-Path -LiteralPath $registrationFile) {
    $registration = Get-Content $registrationFile -Raw | ConvertFrom-Json
} else {
    $body = @{ runner_type='project_type'; project_id=$bootstrap.project_id;
        description='Carlauncher AAOS simpleperf'; tag_list=@('carlauncher-device');
        run_untagged=$false; locked=$true; access_level='not_protected' } | ConvertTo-Json
    $registration = Invoke-RestMethod -Method Post -Uri 'http://localhost:8929/api/v4/user/runners' -Headers $headers -ContentType 'application/json' -Body $body
    $registration | ConvertTo-Json | Set-Content -Encoding utf8 $registrationFile
}
$pwshDirectory = Split-Path (Get-Command pwsh).Source
$pythonDirectory = Split-Path (Get-Command python).Source
$env:PATH = "$pwshDirectory;$env:PATH"
$jobEnvironment = @(
    "ANDROID_HOME=$($Sdk.Replace('\','/'))", "ANDROID_SDK_ROOT=$($Sdk.Replace('\','/'))",
    "JAVA_HOME=$($JavaHome.Replace('\','/'))", "PERF_SERIAL=$Serial",
    'GIT_CONFIG_COUNT=1', 'GIT_CONFIG_KEY_0=core.longpaths', 'GIT_CONFIG_VALUE_0=true',
    'NO_PROXY=localhost,127.0.0.1,192.168.112.1',
    "PATH=$JavaHome/bin;$Sdk/platform-tools;$pythonDirectory;$env:PATH"
)
$environmentToml = ($jobEnvironment | ForEach-Object { ConvertTo-Json -InputObject $_ -Compress }) -join ', '
$builds = (Join-Path $local 'device-builds').Replace('\','/')
$configPath = Join-Path $runnerDirectory 'config.toml'
@"
concurrent = 1
check_interval = 3
[[runners]]
  name = "Carlauncher AAOS simpleperf"
  url = "http://localhost:8929"
  clone_url = "http://localhost:8929"
  token = "$($registration.token)"
  executor = "shell"
  shell = "pwsh"
  builds_dir = "$builds"
  request_concurrency = 2
  environment = [$environmentToml]
  [runners.custom_build_dir]
    enabled = true
"@ | Set-Content -Encoding utf8 $configPath
& $exe verify --config $configPath
if ($LASTEXITCODE -ne 0) { throw 'Device Runner verification failed' }
$pidFile = Join-Path $runnerDirectory 'runner.pid'
if (Test-Path -LiteralPath $pidFile) {
    $oldPid = [int](Get-Content $pidFile -Raw)
    $oldProcess = Get-Process -Id $oldPid -ErrorAction SilentlyContinue
    if ($oldProcess -and $oldProcess.Path -eq $exe) { Write-Output "Device Runner already running (PID $oldPid)"; return }
}
$process = Start-Process -FilePath $exe -ArgumentList @('run','--config',('"{0}"' -f $configPath),'--working-directory',('"{0}"' -f $runnerDirectory)) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runnerDirectory 'stdout.log') -RedirectStandardError (Join-Path $runnerDirectory 'stderr.log')
$process.Id | Set-Content $pidFile
Write-Output "Device Runner started (PID $($process.Id)); target $Serial"
