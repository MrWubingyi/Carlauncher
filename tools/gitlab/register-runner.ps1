$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$state = Get-Content (Join-Path $repo '.gitlab-local/bootstrap.json') -Raw | ConvertFrom-Json
# Transfer secrets through stdin, never put the token in process arguments.
$config = @"
concurrent = 1
check_interval = 3
[[runners]]
  name = "Carlauncher local Docker"
  url = "http://gitlab:8929"
  clone_url = "http://gitlab:8929"
  token = "$($state.runner_token)"
  executor = "docker"
  request_concurrency = 2
  [runners.docker]
    image = "carlauncher-android-ci:local"
    pull_policy = "if-not-present"
    network_mode = "carlauncher-gitlab"
    privileged = false
    volumes = ["/cache"]
    memory = "6g"
    cpus = "4"
"@
$config | docker exec -i carlauncher-gitlab-runner sh -c 'umask 077; cat > /etc/gitlab-runner/config.toml'
if ($LASTEXITCODE -ne 0) { throw 'Runner configuration failed' }
docker exec carlauncher-gitlab-runner gitlab-runner verify
if ($LASTEXITCODE -ne 0) { throw 'Runner verification failed' }
