param([switch]$Start)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$state = Get-Content (Join-Path $repo '.gitlab-local/bootstrap.json') -Raw | ConvertFrom-Json
$headers = @{ 'PRIVATE-TOKEN' = $state.api_token }
$url = "http://localhost:8929/api/v4/projects/$($state.project_id)"
if ($Start) {
    Invoke-RestMethod -Method Put -Uri $url -Headers $headers -ContentType 'application/json' -Body (@{ default_branch = 'codex/gitlab-ci' } | ConvertTo-Json) | Out-Null
    $pipeline = Invoke-RestMethod -Method Post -Uri "$url/pipeline" -Headers $headers -ContentType 'application/json' -Body (@{ ref = 'codex/gitlab-ci' } | ConvertTo-Json)
} else {
    $pipeline = (Invoke-RestMethod -Uri "$url/pipelines?ref=codex%2Fgitlab-ci&per_page=1" -Headers $headers)[0]
}
if (-not $pipeline) { throw 'No snapshot pipeline found' }
$pipeline | Select-Object id, status, web_url
$jobs = Invoke-RestMethod -Uri "$url/pipelines/$($pipeline.id)/jobs" -Headers $headers
$jobs | Select-Object id, name, status, web_url
foreach ($job in $jobs) {
    if ($job.status -in @('running', 'failed', 'success')) {
        $trace = Invoke-RestMethod -Uri "$url/jobs/$($job.id)/trace" -Headers $headers
        $trace | Set-Content -Encoding utf8 (Join-Path $repo ".gitlab-local/job-$($job.id).log")
        ($trace -split "`n" | Select-Object -Last 20) -join "`n"
    }
}
