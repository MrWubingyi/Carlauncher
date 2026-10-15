#!/usr/bin/env python3
"""Convert actual scanner/benchmark output to GitLab CE reports (stdlib only)."""
import argparse
import hashlib
import html
import json
import math
import os
import platform
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def write_reports(output, title, checks, details):
    output.mkdir(parents=True, exist_ok=True)
    suite = ET.Element('testsuite', name=title, tests=str(len(checks)),
                       failures=str(sum(state == 'failed' for _, state, _ in checks)),
                       skipped=str(sum(state == 'skipped' for _, state, _ in checks)))
    for name, state, message in checks:
        case = ET.SubElement(suite, 'testcase', classname=title, name=name)
        if state == 'failed':
            ET.SubElement(case, 'failure', message=message).text = message
        elif state == 'skipped':
            ET.SubElement(case, 'skipped', message=message)
        ET.SubElement(case, 'system-out').text = message
    ET.ElementTree(suite).write(output / 'junit.xml', encoding='utf-8', xml_declaration=True)
    result = {'commit': os.getenv('CI_COMMIT_SHA'), 'job': os.getenv('CI_JOB_URL'),
              'runner': {'id': os.getenv('CI_RUNNER_ID'), 'name': os.getenv('CI_RUNNER_DESCRIPTION'),
                         'image': os.getenv('CI_JOB_IMAGE'), 'platform': platform.platform()},
              'checks': checks, 'details': details}
    (output / 'summary.json').write_text(json.dumps(result, indent=2, allow_nan=False), encoding='utf-8')
    table = ''.join('<tr><td>' + '</td><td>'.join(html.escape(str(v)) for v in row) + '</td></tr>'
                    for row in checks)
    (output / 'index.html').write_text('<!doctype html><meta charset="utf-8"><title>'
        + html.escape(title) + '</title><style>body{font:16px system-ui;margin:2rem}td{padding:8px;border:1px solid #aaa}pre{white-space:pre-wrap}</style>'
        + '<h1>' + html.escape(title) + '</h1><p>Commit: ' + html.escape(result['commit'] or 'local')
        + '</p><table>' + table + '</table><h2>Measurements / findings</h2><pre>'
        + html.escape(json.dumps(details, indent=2, allow_nan=False)) + '</pre>', encoding='utf-8')
    job_url = os.getenv('CI_JOB_URL')
    annotations = {'reports': []}
    if job_url:
        relative = output.resolve().relative_to(ROOT).as_posix()
        annotations['reports'].append({'external_link': {'label': title + ' HTML report',
            'url': f'{job_url}/artifacts/file/{relative}/index.html'}})
    (output / 'annotations.json').write_text(json.dumps(annotations), encoding='utf-8')
    return int(any(state == 'failed' for _, state, _ in checks))


def relative_path(value):
    path = Path(value.removeprefix('file://'))
    if path.is_absolute():
        path = path.resolve().relative_to(ROOT)
    if '..' in path.parts:
        raise ValueError(f'Non-project finding: {value}')
    return path.as_posix().removeprefix('./')


def review(output, exit_code):
    findings, reports, present = [], [], set()
    for module in ('app', 'someip-android'):
        paths = list((ROOT / module / 'build/reports/detekt').glob('*.xml'))
        paths += list((ROOT / module / 'build/reports').glob('lint-results-*.xml'))
        for path in paths:
            document = ET.parse(path).getroot()
            reports.append(path.relative_to(ROOT).as_posix())
            if document.tag == 'checkstyle':
                present.add((module, 'detekt'))
                entries = [(node.get('name'), issue.get('line', '1'), issue.get('source', 'detekt'),
                            issue.get('message', ''), issue.get('severity', 'warning'))
                           for node in document.findall('file') for issue in node.findall('error')]
            elif document.tag == 'issues':
                present.add((module, 'lint'))
                entries = [(loc.get('file'), loc.get('line', '1'), issue.get('id', 'lint'),
                            issue.get('message', ''), issue.get('severity', 'warning'))
                           for issue in document.findall('issue') for loc in issue.findall('location')[:1]]
            else:
                continue
            for file, line, rule, message, severity in entries:
                file = relative_path(file)
                fingerprint = hashlib.sha256(f'{file}|{rule}|{line}|{message}'.encode()).hexdigest()
                findings.append({'description': message, 'check_name': rule, 'fingerprint': fingerprint,
                    'severity': 'major' if severity.lower() in ('error', 'fatal') else 'minor',
                    'location': {'path': file, 'lines': {'begin': max(1, int(line))}}})
    checks = [('static_analysis', 'passed' if exit_code == 0 else 'failed',
               f'Detekt/Lint exit={exit_code}, findings={len(findings)}')]
    checks.append(('scanner_reports_present', 'passed' if len(present) == 4 else 'failed',
                   f'Expected Detekt and Lint reports for both modules: {reports}'))
    result = write_reports(output, 'Code review (Detekt + Android Lint)', checks, findings)
    (output / 'codequality.json').write_text(json.dumps(findings, indent=2), encoding='utf-8')
    return result


def jmh(input_file, output):
    data = json.loads(input_file.read_text(encoding='utf-8'))
    checks, measurements = [], []
    for item in data:
        metric = item['primaryMetric']
        score = float(metric['score'])
        valid = math.isfinite(score) and score > 0 and item['forks'] >= 2
        measurement = {key: item[key] for key in ('benchmark', 'jvm', 'jdkVersion', 'jmhVersion',
                       'warmupIterations', 'measurementIterations', 'forks')}
        measurement.update(score=score if math.isfinite(score) else None, unit=metric['scoreUnit'])
        error = metric.get('scoreError')
        measurement['score_error'] = error if isinstance(error, (float, int)) and math.isfinite(error) else None
        allocation = item.get('secondaryMetrics', {}).get('gc.alloc.rate.norm', {}).get('score')
        measurement['allocation_bytes_per_op'] = allocation if isinstance(allocation, (float, int)) and math.isfinite(allocation) else None
        measurements.append(measurement)
        checks.append((item['benchmark'], 'passed' if valid else 'failed', json.dumps(measurement)))
    checks.append(('measurements_present', 'passed' if len(data) >= 4 else 'failed', f'{len(data)} benchmarks'))
    checks.append(('regression_budget', 'skipped',
                   'No approved same-runner baseline/budget configured. Collection success is not a performance regression verdict.'))
    return write_reports(output, 'JVM performance (JMH)', checks, measurements)


def simpleperf(input_dir, output):
    summary = json.loads((input_dir / 'summary.json').read_text(encoding='utf-8'))
    metadata = json.loads((input_dir / 'metadata.json').read_text(encoding='utf-8'))
    valid = summary['samples'] > 0 and summary['sampled_cpu_seconds'] > 0
    provenance = metadata.get('git_head') == os.getenv('CI_COMMIT_SHA', metadata.get('git_head'))
    checks = [('actual_cpu_samples', 'passed' if valid else 'failed',
               f"samples={summary['samples']}, sampled_user_cpu_seconds={summary['sampled_cpu_seconds']}"),
              ('dashboard_actions_executed', 'passed' if metadata.get('recorded_actions', 0) > 0 else 'failed',
               f"actions={metadata.get('recorded_actions', 0)}"),
              ('commit_provenance', 'passed' if provenance else 'failed', str(metadata.get('git_head'))),
              ('regression_budget', 'skipped',
               'No approved fixed-device baseline/budget. CPU samples are not frame latency or a regression verdict.')]
    return write_reports(output, 'Android performance (simpleperf)', checks,
                         {'measurements': summary, 'provenance': metadata})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('kind', choices=('review', 'jmh', 'simpleperf'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--input', type=Path)
    parser.add_argument('--exit-code', type=int, default=0)
    args = parser.parse_args()
    try:
        if args.kind == 'review':
            return review(args.output, args.exit_code)
        if args.kind == 'jmh':
            return jmh(args.input, args.output)
        return simpleperf(args.input, args.output)
    except Exception as error:
        write_reports(args.output, args.kind, [('report_generation', 'failed', str(error))], {})
        print(f'Report generation failed: {error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
