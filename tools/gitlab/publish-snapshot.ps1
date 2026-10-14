param([string]$Branch = 'codex/gitlab-ci')
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location $repo
New-Item -ItemType Directory -Force '.gitlab-local' | Out-Null
$previousIndex = $env:GIT_INDEX_FILE
try {
    $env:GIT_INDEX_FILE = Join-Path $repo '.gitlab-local/snapshot.index'
    git read-tree HEAD
    if ($LASTEXITCODE -ne 0) { throw 'read-tree failed' }
    git add -A
    if ($LASTEXITCODE -ne 0) { throw 'snapshot staging failed' }
    git diff --cached --check
    if ($LASTEXITCODE -ne 0) { throw 'snapshot whitespace check failed' }
    $tree = git write-tree
    if ($LASTEXITCODE -ne 0) { throw 'write-tree failed' }
    $parent = git rev-parse HEAD
    $commit = 'Prepare local GitLab CI and capture current source' | git -c user.name='Codex' -c user.email='codex@localhost' commit-tree $tree -p $parent
    if ($LASTEXITCODE -ne 0) { throw 'snapshot commit failed' }
    git update-ref "refs/heads/$Branch" $commit
    if ($LASTEXITCODE -ne 0) { throw 'snapshot branch failed' }
} finally {
    $env:GIT_INDEX_FILE = $previousIndex
}
$github = 'git@github.com:MrWubingyi/Carlauncher.git'
$remotes = @(git remote)
if ($remotes -notcontains 'upstream') { git remote add upstream $github }
elseif ((git remote get-url upstream) -ne $github) { throw 'Existing upstream differs; refusing to overwrite' }
$localUrl = 'ssh://git@localhost:2229/root/Carlauncher.git'
if ($remotes -notcontains 'gitlab') { git remote add gitlab $localUrl }
elseif ((git remote get-url gitlab) -ne $localUrl) { throw 'Existing gitlab remote differs; refusing to overwrite' }
git -c core.sshCommand='ssh -o StrictHostKeyChecking=accept-new' push gitlab --all
if ($LASTEXITCODE -ne 0) { throw 'Branch upload failed' }
git -c core.sshCommand='ssh -o StrictHostKeyChecking=accept-new' push gitlab --tags
if ($LASTEXITCODE -ne 0) { throw 'Tag upload failed' }
Write-Output "Snapshot branch: $Branch ($commit). Original worktree and index preserved."
