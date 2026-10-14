#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
export GRADLE_USER_HOME=${GRADLE_USER_HOME:-$root/.gradle-ci}
mkdir -p "$GRADLE_USER_HOME/init.d"
cp "$root/tools/gitlab/init.gradle" "$GRADLE_USER_HOME/init.d/carlauncher-local.gradle"
# Seed the wrapper's canonical cache with a byte-identical distribution.
# The repository URL and mandatory SHA-256 remain unchanged.
zip=$(python3 - "$root" <<'PY'
import hashlib, os, pathlib, sys
props = dict(line.strip().split('=', 1) for line in
             (pathlib.Path(sys.argv[1]) / 'gradle/wrapper/gradle-wrapper.properties').read_text().splitlines()
             if '=' in line and not line.startswith('#'))
url = props['distributionUrl'].replace('\\:', ':')
name = url.rsplit('/', 1)[1]
number = int.from_bytes(hashlib.md5(url.encode()).digest(), 'big')
digits = '0123456789abcdefghijklmnopqrstuvwxyz'
key = ''
while number:
    number, digit = divmod(number, 36)
    key = digits[digit] + key
path = pathlib.Path(os.environ['GRADLE_USER_HOME']) / 'wrapper/dists' / name[:-4] / key / name
path.parent.mkdir(parents=True, exist_ok=True)
print(path)
PY
)
sha=$(sed -n 's/^distributionSha256Sum=//p' "$root/gradle/wrapper/gradle-wrapper.properties" | tr -d '\r')
test -n "$sha"
if [[ ! -f "${zip}.ok" ]]; then
  if [[ ! -f "$zip" ]] || ! echo "$sha  $zip" | sha256sum --check; then
    curl --fail --location --retry 3 --retry-all-errors --connect-timeout 20 --max-time 600 \
      "${GRADLE_DOWNLOAD_URL:?Set a reachable mirror for the pinned Gradle distribution}" -o "${zip}.download"
    echo "$sha  ${zip}.download" | sha256sum --check
    mv "${zip}.download" "$zip"
  fi
fi
bash "$root/gradlew" --no-daemon --version
