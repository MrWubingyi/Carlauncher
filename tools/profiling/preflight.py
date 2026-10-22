"""Exact baseline checks before installing or exercising the profiling APK."""
import json
from pathlib import Path

SCENARIO = 'Repeated demo-map zoom/pan/recenter and demo-media next/play/seek/previous'


def differences(expected, actual):
    # JSON normalizes tuples emitted by UI discovery to the stored list format.
    actual = json.loads(json.dumps(actual))
    return [{'field': key, 'expected': value, 'actual': actual.get(key)}
            for key, value in expected.items() if actual.get(key) != value]


def check(expected, actual, output, stage):
    path = Path(output) / 'preflight.json'
    result = json.loads(path.read_text(encoding='utf-8')) if path.exists() else {'stages': []}
    diff = differences(expected, actual)
    result['stages'].append({'stage': stage, 'expected': expected, 'actual': actual,
                             'differences': diff, 'passed': not diff})
    result['passed'] = all(item['passed'] for item in result['stages'])
    path.write_text(json.dumps(result, indent=2), encoding='utf-8')
    for item in diff:
        print(f"Preflight mismatch [{stage}] {item['field']}: "
              f"expected={json.dumps(item['expected'])}; actual={json.dumps(item['actual'])}", flush=True)
    if diff:
        raise RuntimeError(f'Preflight failed ({stage}); no warmup or sampling started; see {path}')


def device_config(device, serial, duration, warmup, frequency):
    def read(*args):
        try:
            return device(*args)
        except Exception as error:
            return f'UNAVAILABLE: {error}'
    return {'serial': serial, 'fingerprint': read('shell', 'getprop', 'ro.build.fingerprint'),
            'screen': read('shell', 'wm', 'size'), 'duration_seconds': duration,
            'warmup_seconds': warmup, 'scenario': SCENARIO, 'event': 'task-clock:u',
            'record_options': f'-e task-clock:u -f {frequency} -g --duration {duration}'}
