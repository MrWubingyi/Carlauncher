#!/usr/bin/env bash
set -euo pipefail
python3 tools/gitlab/test-reports.py
python3 tools/profiling/test-flamegraph.py
mkdir -p build/reports/code-review
status=0
bash ./gradlew --no-daemon --max-workers=2 --stacktrace --continue \
  :app:detekt :someip-android:detekt :app:lintDebug :someip-android:lintDebug \
  > build/reports/code-review/scanners.log 2>&1 || status=$?
tail -n 60 build/reports/code-review/scanners.log
python3 tools/gitlab/reports.py review --output build/reports/code-review --exit-code "$status"
