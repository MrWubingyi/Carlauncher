$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location $root
$raw = Join-Path $root 'build/reports/simpleperf/raw'
$report = Join-Path $root 'build/reports/simpleperf/gitlab'
try {
    if (-not $env:PERF_SERIAL) { throw 'PERF_SERIAL must identify the dedicated Android device' }
    if (-not $env:ANDROID_HOME) { throw 'ANDROID_HOME must identify the device runner SDK' }
    $ndk = Join-Path $env:ANDROID_HOME 'ndk/28.2.13676358'
    & python tools/profiling/record-dashboard.py --sdk $env:ANDROID_HOME --ndk $ndk --serial $env:PERF_SERIAL --duration 60 --warmup 15 --replay-plan tools/profiling/dashboard-replay.json --output $raw
    if ($LASTEXITCODE -ne 0) { throw 'simpleperf recording failed' }
    & python tools/profiling/summarize.py --ndk $ndk $raw
    if ($LASTEXITCODE -ne 0) { throw 'simpleperf summary failed' }
    & python tools/gitlab/reports.py simpleperf --input $raw --output $report
    if ($LASTEXITCODE -ne 0) { throw 'simpleperf measurement validation failed' }
} catch {
    Write-Output $_.Exception.Message
    # Missing/invalid samples produce a failing JUnit report as well as a failed job.
    & python tools/gitlab/reports.py simpleperf --input $raw --output $report
    exit 1
}
