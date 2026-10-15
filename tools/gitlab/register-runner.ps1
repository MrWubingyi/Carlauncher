$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$state = Get-Content (Join-Path $repo '.gitlab-local/bootstrap.json') -Raw | ConvertFrom-Json
# A dedicated persistent debug key keeps repeated profiling APK installs compatible.
# This is not a production/release signing key and never enters repository artifacts.
docker volume create carlauncher-debug-signing | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Debug signing volume creation failed' }
docker run --rm --volume carlauncher-debug-signing:/root/.android carlauncher-android-ci:local bash -lc 'if [ ! -f /root/.android/debug.keystore ]; then keytool -genkeypair -noprompt -keystore /root/.android/debug.keystore -storetype JKS -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname CN=CarLauncher_Debug,O=CarLauncher,C=US; fi'
if ($LASTEXITCODE -ne 0) { throw 'CI debug signing key initialization failed' }
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
    volumes = ["/cache", "carlauncher-debug-signing:/root/.android"]
    memory = "6g"
    cpus = "4"
"@
$config | docker exec -i carlauncher-gitlab-runner sh -c 'umask 077; cat > /etc/gitlab-runner/config.toml'
if ($LASTEXITCODE -ne 0) { throw 'Runner configuration failed' }
docker exec carlauncher-gitlab-runner gitlab-runner verify
if ($LASTEXITCODE -ne 0) { throw 'Runner verification failed' }
