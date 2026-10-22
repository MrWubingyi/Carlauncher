"""Preflight must fail before install or workload when the baseline does not match."""
import copy
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
from preflight import check, differences, device_config

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('recorder', Path(__file__).with_name('record-dashboard.py'))
recorder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(recorder)


class PreflightTests(unittest.TestCase):
    def setUp(self):
        self.base = json.loads((ROOT / 'tools/gitlab/performance-baseline.json').read_text())
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.output = Path(self.directory.name)

    def test_all_baseline_fields_and_multiple_differences(self):
        expected = self.base['simpleperf']['config']
        check(expected, expected, self.output, 'device')
        for field in expected:
            actual = dict(expected, **{field: 'changed'})
            self.assertEqual([field], [row['field'] for row in differences(expected, actual)])
        actual = dict(expected, fingerprint='Android 15', screen='different', duration_seconds=10)
        with self.assertRaises(RuntimeError):
            check(expected, actual, self.output, 'device')
        result = json.loads((self.output / 'preflight.json').read_text())
        self.assertFalse(result['passed'])
        self.assertEqual(3, len(result['stages'][-1]['differences']))

    def test_offline_device_is_explicit(self):
        def offline(*args):
            raise RuntimeError('device offline')
        actual = device_config(offline, 'emulator-5554', 60, 15, 1000)
        self.assertIn('UNAVAILABLE: device offline', actual['fingerprint'])

    def test_android15_fails_before_install_or_sampler(self):
        self.run_recorder('Android 15', action_mismatch=False)

    def test_live_action_mismatch_fails_before_warmup_or_sampler(self):
        self.run_recorder(self.base['simpleperf']['config']['fingerprint'], action_mismatch=True)

    def run_recorder(self, fingerprint, action_mismatch):
        sdk, ndk = self.output / 'sdk', self.output / 'ndk'
        apk = self.output / 'app/build/outputs/apk/profiling/app-profiling.apk'
        for file in (sdk / 'platform-tools' / ('adb.exe' if sys.platform == 'win32' else 'adb'),
                     ndk / 'simpleperf/app_profiler.py', apk):
            file.parent.mkdir(parents=True, exist_ok=True)
            file.touch()
        calls = []
        def fake_run(command, **kwargs):
            calls.append([str(item) for item in command])
            if 'ro.build.fingerprint' in command:
                return fingerprint.encode()
            if 'size' in command:
                return b'Physical size: 1408x792'
            if 'ro.build.version.sdk' in command:
                return b'33'
            return b'fixture'
        actions = copy.deepcopy(self.base['simpleperf']['config']['action_plan'])
        actions[0][1][1] += 1
        argv = ['record', '--sdk', str(sdk), '--ndk', str(ndk), '--serial', 'emulator-5554',
                '--baseline', str(ROOT / 'tools/gitlab/performance-baseline.json'),
                '--output', str(self.output / 'raw')]
        with patch.object(recorder, 'ROOT', self.output), patch.object(sys, 'argv', argv), \
             patch.object(recorder, 'run', side_effect=fake_run), \
             patch.object(recorder.subprocess, 'run') as subprocess_run, \
             patch.object(recorder.subprocess, 'Popen') as popen, \
             patch.object(recorder, 'dashboard_actions', return_value=actions):
            subprocess_run.return_value.returncode = 1
            with self.assertRaisesRegex(RuntimeError, 'Preflight failed'):
                recorder.main()
            popen.assert_not_called()
            if not action_mismatch:
                subprocess_run.assert_not_called()
                self.assertFalse(any('install' in cmd for cmd in calls))
            self.assertFalse(any('input' in cmd or 'push' in cmd for cmd in calls))
            self.assertFalse((self.output / 'raw/perf.data').exists())


if __name__ == '__main__':
    unittest.main()
