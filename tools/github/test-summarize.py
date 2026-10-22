"""Exercise summary success, failure and unavailable evidence without running Android."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('summary', Path(__file__).with_name('summarize.py'))
summary = importlib.util.module_from_spec(spec)
spec.loader.exec_module(summary)


class SummaryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.env = patch.dict(os.environ, {'GITHUB_SHA': 'abc', 'JOB_STATUS': 'success',
                                          'REPORT_URL': 'https://github.com/a/b/actions/runs/1/artifacts/2'})
        self.env.start()
        self.addCleanup(self.env.stop)

    def write(self, path, data):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(data), encoding='utf-8')

    def performance(self, checks=None, commit='abc', nested=''):
        self.write(f'build/reports/jvm-performance/{nested}summary.json',
                   {'commit': commit, 'checks': checks or [['measurements_present', 'passed', 'measurements=5']]})

    def ui(self):
        self.write('build/ui-regression/ci/run/run.json', {'commit': 'abc', 'capture_exit': 0, 'verify_exit': 0})
        self.write('build/ui-regression/ci/run/pixel-results.json', {
            'scenes': [{'scene': name, 'title': name, 'changed_pixels': 0} for name in summary.SCENES],
            'changed_scenes': 0, 'incomplete_scenes': 0})

    def test_performance_success(self):
        self.performance()
        self.assertEqual(summary.collect('jmh', self.root)['state'], 'passed')

    def test_threshold_failure_preserved(self):
        self.performance([['measurements_present', 'passed', 'yes'], ['budget', 'failed', 'limit=10; actual=12']])
        result = summary.collect('jmh', self.root)
        self.assertEqual(result['state'], 'failed')
        self.assertIn('limit=10', result['checks'][1][2])

    def test_task_failure_overrides_passing_report(self):
        self.performance()
        os.environ['JOB_STATUS'] = 'failure'
        self.assertEqual(summary.collect('jmh', self.root)['state'], 'failed')

    def test_missing_report(self):
        self.assertEqual(summary.collect('jmh', self.root)['state'], 'unknown')

    def test_runner_only_is_not_gate_success(self):
        self.performance(nested='runner/')
        self.assertEqual(summary.collect('jmh', self.root)['state'], 'unknown')

    def test_stale_commit(self):
        self.performance(commit='old')
        self.assertEqual(summary.collect('jmh', self.root)['state'], 'unknown')

    def test_ui_success(self):
        self.ui()
        self.assertEqual(summary.collect('ui', self.root)['state'], 'passed')

    def test_ui_capture_failure(self):
        self.ui()
        self.write('build/ui-regression/ci/run/run.json', {'commit': 'abc', 'capture_exit': 1, 'verify_exit': 0})
        self.assertEqual(summary.collect('ui', self.root)['state'], 'failed')

    def test_ui_missing_scene(self):
        self.ui()
        path = 'build/ui-regression/ci/run/pixel-results.json'
        data = summary.read(self.root / path)
        data['scenes'].pop()
        self.write(path, data)
        self.assertEqual(summary.collect('ui', self.root)['state'], 'failed')

    def test_skipped_not_accepted(self):
        document = summary.render({job: {'result': 'skipped'} for job in summary.JOBS})
        self.assertIn('验收不完整', document)
        self.assertNotIn('四项验收通过', document)

    def test_render_failure_and_escape(self):
        result = {'state': 'passed', 'brief': '<script>|x', 'links': {'报告下载': 'javascript:alert(1)'}}
        document = summary.render({'build': {'result': 'failure', 'outputs': {'acceptance': json.dumps(result)}}})
        self.assertIn('验收未通过', document)
        self.assertIn('&lt;script&gt;&#124;x', document)
        self.assertNotIn('javascript:', document)

    def test_download_links_and_full_acceptance(self):
        self.performance()
        result = summary.collect('jmh', self.root)
        document = summary.render({job: {'result': 'success', 'outputs': {'acceptance': json.dumps(result)}}
                                   for job in summary.JOBS})
        self.assertIn('四项验收通过', document)
        self.assertIn('[报告下载](https://github.com/', document)

    def test_corrupt_output(self):
        document = summary.render({'build': {'result': 'success', 'outputs': {'acceptance': 'broken'}}})
        self.assertIn('证据缺失', document)

    def test_build_requires_both_module_tests(self):
        self.write('build/reports/code-review/summary.json', {'commit': 'abc', 'checks': [['lint', 'passed', 'ok']]})
        self.assertEqual(summary.collect('build', self.root)['state'], 'unknown')
        for module in ('app', 'someip-android'):
            target = self.root / module / 'build/test-results/testDebugUnitTest/TEST-example.xml'
            target.parent.mkdir(parents=True)
            target.write_text('<testsuite tests="3" failures="0" errors="0" skipped="1"/>')
        self.assertEqual(summary.collect('build', self.root)['state'], 'passed')


if __name__ == '__main__':
    unittest.main()
