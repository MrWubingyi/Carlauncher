#!/usr/bin/env python3
"""Publish existing review/JUnit results without changing any scanner gates."""
import html
import json
import os
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def main():
    lines = ['## Android checks', '',
             'JMH and Android simpleperf run in GitLab only; no performance measurements run here.', '']
    review = ROOT / 'build/reports/code-review/summary.json'
    if review.is_file():
        data = json.loads(review.read_text(encoding='utf-8'))
        lines += ['### Detekt / Checkstyle / Android Lint', '', '| Check | Result |', '| --- | --- |']
        for name, state, _ in data['checks']:
            lines.append(f'| {html.escape(name)} | {state} |')
        lines += ['', f"Findings: **{len(data['details'])}** (details in report artifacts).", '']
    else:
        lines += ['Review report unavailable; inspect the failed setup or build step.', '']
    lines += ['### JVM unit tests', '', '| Module | Tests | Failures | Errors | Skipped |',
              '| --- | ---: | ---: | ---: | ---: |']
    for module in ('app', 'someip-android'):
        paths = list((ROOT / module / 'build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
        if not paths:
            lines.append(f'| {module} | unavailable | — | — | — |')
            continue
        totals = {key: 0 for key in ('tests', 'failures', 'errors', 'skipped')}
        for path in paths:
            root = ET.parse(path).getroot()
            suites = [root] if root.tag == 'testsuite' else list(root.findall('testsuite'))
            for suite in suites:
                for key in totals:
                    totals[key] += int(suite.get(key, '0'))
        lines.append('| ' + module + ' | ' + ' | '.join(str(v) for v in totals.values()) + ' |')
    lines += ['', 'APK/AAR, native provenance, XML/HTML reports and coverage are uploaded as workflow artifacts.',
              'Coverage report generation does not execute the 90%/85% coverage verification gate.', '']
    document = '\n'.join(lines)
    summary = os.getenv('GITHUB_STEP_SUMMARY')
    if summary:
        with Path(summary).open('a', encoding='utf-8') as stream:
            stream.write(document)
    else:
        print(document)


if __name__ == '__main__':
    main()
