#!/usr/bin/env bash
set -euo pipefail
status=0
if [[ -n "${CI:-}" ]]; then
  python3 tools/gitlab/reports.py runner --benchmark jmh --output build/reports/jvm-performance
fi
bash ./gradlew --no-daemon --max-workers=2 --stacktrace :app:jvmPerformanceReport || status=$?
python3 tools/gitlab/reports.py jmh --input app/build/reports/jmh/results.json \
  --output build/reports/jvm-performance
exit "$status"
