#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == "--inside" ]]; then
  # Return generated files to the checkout owner, including after a failed gate.
  finish() {
    result=$?
    for directory in app/build someip-android/build build .gradle-ci .gradle; do
      if [[ -d "$directory" ]]; then
        chown -R "$CHECKOUT_UID:$CHECKOUT_GID" "$directory"
      fi
    done
    exit "$result"
  }
  trap finish EXIT
  python3 tools/gitlab/reports.py runner --benchmark jmh --output build/reports/jvm-performance
  test -f /root/.android/debug.keystore
  bash tools/gitlab/prepare-gradle.sh
  bash ./gradlew --no-daemon --max-workers=2 --stacktrace :app:assembleProfiling
  bash tools/gitlab/run-jmh.sh
else
  # Job containers always pull; use the exact local image shared with GitLab.
  docker image inspect carlauncher-android-ci:local >/dev/null
  docker run --rm --pull=never --cpus 2 --memory 6g \
    --volume "$PWD:/workspace" --workdir /workspace \
    --volume carlauncher-debug-signing:/root/.android \
    --env CI --env GITHUB_ACTIONS --env GITHUB_SHA --env GITHUB_REPOSITORY \
    --env GITHUB_SERVER_URL --env GITHUB_RUN_ID --env RUNNER_NAME \
    --env BASELINE_GITHUB_RUNNER_NAME --env GRADLE_DOWNLOAD_URL \
    --env ANDROID_USER_HOME=/root/.android --env GRADLE_USER_HOME=/workspace/.gradle-ci \
    --env CHECKOUT_UID="$(id -u)" --env CHECKOUT_GID="$(id -g)" \
    carlauncher-android-ci:local bash tools/github/run-performance-build.sh --inside
fi
