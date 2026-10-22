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
sys.path.insert(0, str(ROOT / "tools/profiling"))
from replay import validate_results
from preflight import differences


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
    github_run = (f"{os.getenv('GITHUB_SERVER_URL', 'https://github.com')}/{os.getenv('GITHUB_REPOSITORY')}/actions/runs/{os.getenv('GITHUB_RUN_ID')}"
                  if os.getenv('GITHUB_RUN_ID') else None)
    result = {'commit': os.getenv('CI_COMMIT_SHA') or os.getenv('GITHUB_SHA'), 'job': os.getenv('CI_JOB_URL') or github_run,
              'runner': {'id': os.getenv('CI_RUNNER_ID'), 'name': os.getenv('CI_RUNNER_DESCRIPTION') or os.getenv('RUNNER_NAME'),
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
        paths += list((ROOT / module / 'build/reports/checkstyle').glob('*.xml'))
        paths += list((ROOT / module / 'build/reports').glob('lint-results-*.xml'))
        for path in paths:
            document = ET.parse(path).getroot()
            reports.append(path.relative_to(ROOT).as_posix())
            if document.tag == 'checkstyle':
                present.add((module, 'checkstyle' if path.parent.name == 'checkstyle' else 'detekt'))
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
               f'Detekt/Checkstyle/Lint exit={exit_code}, findings={len(findings)}')]
    expected = {(m, scanner) for m in ('app', 'someip-android')
                for scanner in ('detekt', 'lint')}
    expected.update((m, 'checkstyle') for m in ('app', 'someip-android')
                    if any((ROOT / m / 'src').rglob('*.java')))
    checks.append(('scanner_reports_present', 'passed' if expected <= present else 'failed',
                   f'Expected Detekt/Lint reports and Checkstyle reports for modules with Java: {reports}'))
    result = write_reports(output, 'Code review (Detekt + Checkstyle + Android Lint)', checks, findings)
    (output / 'codequality.json').write_text(json.dumps(findings, indent=2), encoding='utf-8')
    return result


def baseline():
    data = json.loads((ROOT / 'tools/gitlab/performance-baseline.json').read_text(encoding='utf-8'))
    if data['schema'] != 1:
        raise ValueError('Unsupported baseline schema')
    return data


def runner_check(kind, base):
    if os.getenv('GITHUB_ACTIONS') == 'true':
        expected = os.getenv('BASELINE_GITHUB_RUNNER_NAME', '')
        actual = os.getenv('RUNNER_NAME', '')
        passed = bool(expected) and actual == expected
        message = f'Approved same-host GitHub runner: expected={expected!r}; actual={actual!r}'
    else:
        expected = base['runner_ids'][kind]
        actual = os.getenv('CI_RUNNER_ID')
        passed = actual == expected
        message = f'GitLab runner: expected={expected!r}; actual={actual!r}'
    return ('baseline_runner', 'passed' if passed else 'failed', message)


def compare(checks, name, current, reference, percent, unit, source, absolute=0):
    values = (current, reference, percent, absolute)
    if not all(isinstance(v, (int, float)) and math.isfinite(v) for v in values) or reference <= 0 or current < 0 or percent < 0 or absolute < 0:
        raise ValueError(f'Invalid regression inputs: {name}')
    limit = reference + max(reference * percent / 100, absolute)
    delta = (current / reference - 1) * 100
    message = (f'baseline={reference:.6g} {unit}; current={current:.6g} {unit}; '
               f'delta={delta:+.2f}%; threshold=+{percent}% (absolute allowance={absolute} {unit}); limit={limit:.6g} {unit}; '
               f'baseline pipeline={source["pipeline"]} commit={source["commit"]}')
    checks.append((name, 'passed' if current <= limit else 'failed', message))


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
    base = baseline()
    if os.getenv('CI'):
        checks.append(runner_check('jmh', base))
    expected = base['jmh']
    names = [item['benchmark'] for item in data]
    checks.append(('baseline_benchmark_set', 'passed' if len(names) == len(set(names)) and set(names) == set(expected) else 'failed', str(names)))
    for item in data:
        name = item['benchmark']
        if name not in expected:
            continue
        ref = expected[name]
        compatible = all(item.get(k) == v for k, v in ref['config'].items()) and item['primaryMetric']['scoreUnit'] == ref['unit']
        checks.append((name + '.configuration', 'passed' if compatible else 'failed', 'Configuration must match baseline: ' + json.dumps(ref['config'])))
        compare(checks, name + '.time_regression', item['primaryMetric']['score'], ref['score'], base['thresholds']['jmh_time_percent'], ref['unit'], base['source'])
        compare(checks, name + '.allocation_regression', item['secondaryMetrics']['gc.alloc.rate.norm']['score'], ref['allocation'], base['thresholds']['jmh_allocation_percent'], 'B/op', base['source'], base['thresholds']['jmh_allocation_absolute_bytes'])
    return write_reports(output, 'JVM performance (JMH)', checks, measurements)


def simpleperf(input_dir, output, exit_code=0):
    preflight_file = input_dir / 'preflight.json'
    if preflight_file.exists():
        preflight = json.loads(preflight_file.read_text(encoding='utf-8'))
        if not preflight['passed']:
            checks = [(f"preflight.{stage['stage']}.{item['field']}", 'failed',
                       f"expected={json.dumps(item['expected'])}; actual={json.dumps(item['actual'])}")
                      for stage in preflight['stages'] for item in stage['differences']]
            return write_reports(output, 'simpleperf preflight failed (not sampled)', checks, preflight)
    if exit_code:
        return write_reports(output, 'simpleperf execution failed',
                             [('recording_process', 'failed', f'exit={exit_code}; see job log')], {})
    summary = json.loads((input_dir / 'summary.json').read_text(encoding='utf-8'))
    metadata = json.loads((input_dir / 'metadata.json').read_text(encoding='utf-8'))
    valid = summary['samples'] > 0 and summary['sampled_cpu_seconds'] > 0
    provenance = metadata.get('git_head') == (os.getenv('CI_COMMIT_SHA') or os.getenv('GITHUB_SHA') or metadata.get('git_head'))
    checks = [('actual_cpu_samples', 'passed' if valid else 'failed',
               f"samples={summary['samples']}, sampled_user_cpu_seconds={summary['sampled_cpu_seconds']}"),
              ('dashboard_actions_executed', 'passed' if metadata.get('recorded_actions', 0) > 0 else 'failed',
               f"actions={metadata.get('recorded_actions', 0)}"),
              ('commit_provenance', 'passed' if provenance else 'failed', str(metadata.get('git_head'))),
              ]
    base = baseline()
    if os.getenv('CI'):
        checks.append(runner_check('simpleperf', base))
    ref = base['simpleperf']
    command = metadata.get('record_command', [])
    options = command[command.index('-r') + 1] if '-r' in command else None
    checks.append(('baseline_record_options', 'passed' if options == ref['record_options'] else 'failed', str(options)))
    mismatches = differences(dict(ref['config'], event=ref['event']), dict(metadata, event=summary.get('event')))
    compatible = not mismatches
    checks.append(('baseline_device_and_scenario', 'passed' if compatible else 'failed',
                   json.dumps(mismatches) if mismatches else 'All device and scenario fields match baseline'))
    # A slower workload must not pass just because it performed fewer actions.
    action_ratio = metadata.get('recorded_actions', 0) / ref['actions']
    checks.append(('workload_comparable', 'passed' if 0.95 <= action_ratio <= 1.05 else 'failed',
                   f'baseline_actions={ref["actions"]}; current_actions={metadata.get("recorded_actions", 0)}; tolerance=5%'))
    try:
        plan_bytes = (ROOT / 'tools/profiling/dashboard-replay.json').read_bytes()
        plan = json.loads(plan_bytes)
        if (metadata.get('replay_mode') != 'device-uptime-v1'
                or metadata.get('replay_plan_sha256') != hashlib.sha256(plan_bytes).hexdigest()
                or plan['source_commit'] != base['source']['commit']):
            raise ValueError('Missing or mismatched fixed baseline replay evidence')
        log = [json.loads(line) for line in (input_dir / 'actions.jsonl').read_text(encoding='utf-8').splitlines()]
        for phase, duration, counter in [('warmup', None, 'warmup_actions'), ('recording', 59, 'recorded_actions')]:
            completed = metadata.get(phase + '_replay', [])
            validate_results(completed, plan['phases'][phase], duration)
            if completed != [entry for entry in log if entry['phase'] == phase] or len(completed) != metadata.get(counter):
                raise ValueError('Device action log and metadata disagree')
        checks.append(('device_replay_schedule', 'passed', 'Exact baseline replay: warmup=31, recording=119; all recorded actions completed inside recording window; no truncated actions'))
    except (ValueError, KeyError, OSError) as error:
        checks.append(('device_replay_schedule', 'failed', str(error)))
    compare(checks, 'scene_cpu_regression', summary['sampled_cpu_seconds'], ref['cpu_seconds'],
            base['thresholds']['simpleperf_cpu_percent'], 'user CPU seconds / 60s scene', base['source'])
    result = write_reports(output, 'Android performance (simpleperf)', checks,
                           {'measurements': summary, 'provenance': metadata})
    index = output / 'index.html'
    index.write_text(index.read_text(encoding='utf-8') +
                     '<p><a href="../raw/flamegraph.html">Interactive offline CPU flame graph</a></p>',
                     encoding='utf-8')
    if os.getenv('CI_JOB_URL'):
        annotations = json.loads((output / 'annotations.json').read_text(encoding='utf-8'))
        relative = input_dir.resolve().relative_to(ROOT).as_posix()
        annotations['reports'].append({'external_link': {'label': 'CPU flame graph (offline HTML)',
            'url': f'{os.environ["CI_JOB_URL"]}/artifacts/file/{relative}/flamegraph.html'}})
        (output / 'annotations.json').write_text(json.dumps(annotations), encoding='utf-8')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('kind', choices=('review', 'jmh', 'simpleperf', 'runner'))
    parser.add_argument('--benchmark', choices=('jmh', 'simpleperf'))
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--input', type=Path)
    parser.add_argument('--exit-code', type=int, default=0)
    args = parser.parse_args()
    try:
        if args.kind == 'runner':
            if not args.benchmark:
                raise ValueError('--benchmark is required for runner validation')
            return write_reports(args.output, 'Performance runner preflight',
                                 [runner_check(args.benchmark, baseline())], {})
        if args.kind == 'review':
            return review(args.output, args.exit_code)
        if args.kind == 'jmh':
            return jmh(args.input, args.output)
        return simpleperf(args.input, args.output, args.exit_code)
    except Exception as error:
        write_reports(args.output, args.kind, [('report_generation', 'failed', str(error))], {})
        print(f'Report generation failed: {error}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
