"""Verify report gates reject missing, malformed and non-finite measurements."""
import importlib.util
import json
from pathlib import Path
import tempfile
import sys
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('reports', Path(__file__).with_name('reports.py'))
reports = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reports)


class ReportTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.output = self.root / 'report'
        self.root_patch = patch.object(reports, 'ROOT', self.root)
        self.root_patch.start()
        environment = patch.dict('os.environ', {'CI': ''})
        environment.start()
        self.addCleanup(environment.stop)
        target = self.root / 'tools/gitlab'
        target.mkdir(parents=True)
        import shutil
        shutil.copyfile(Path(__file__).with_name('performance-baseline.json'), target / 'performance-baseline.json')
        replay_target = self.root / 'tools/profiling'
        replay_target.mkdir(parents=True)
        shutil.copyfile(Path(__file__).parents[1] / 'profiling/dashboard-replay.json', replay_target / 'dashboard-replay.json')
        self.addCleanup(self.root_patch.stop)

    def test_missing_scanners_cannot_be_a_clean_review(self):
        self.assertEqual(1, reports.review(self.output, 0))
        self.assertEqual('1', ET.parse(self.output / 'junit.xml').getroot().get('failures'))

    def test_findings_map_to_source_and_escape_html(self):
        for module in ('app', 'someip-android'):
            base = self.root / module / 'build/reports'
            (base / 'detekt').mkdir(parents=True)
            (base / 'detekt/detekt.xml').write_text('<checkstyle><file name="app/src/A.kt"><error line="7" severity="warning" source="Rule" message="&lt;script&gt;"/></file></checkstyle>')
            (base / 'lint-results-debug.xml').write_text('<issues/>')
        self.assertEqual(0, reports.review(self.output, 0))
        findings = json.loads((self.output / 'codequality.json').read_text())
        self.assertEqual('app/src/A.kt', findings[0]['location']['path'])
        self.assertEqual(7, findings[0]['location']['lines']['begin'])
        self.assertNotIn('<script>', (self.output / 'index.html').read_text())

    def test_checkstyle_report_required_for_java_and_findings_published(self):
        self.test_findings_map_to_source_and_escape_html()
        java = self.root / 'app/src/jmh/java/Example.java'
        java.parent.mkdir(parents=True)
        java.write_text('class Example {}')
        self.assertEqual(1, reports.review(self.output, 0))
        report = self.root / 'app/build/reports/checkstyle/checkstyleAll.xml'
        report.parent.mkdir(parents=True)
        report.write_text('<checkstyle><file name="app/src/jmh/java/Example.java"><error line="1" severity="error" source="AvoidStarImport" message="Wildcard import"/></file></checkstyle>')
        self.assertEqual(1, reports.review(self.output, 1))
        findings = json.loads((self.output / 'codequality.json').read_text())
        self.assertTrue(any(row['check_name'] == 'AvoidStarImport' for row in findings))
        report.write_text('<checkstyle/>')
        self.assertEqual(0, reports.review(self.output, 0))

    def test_empty_jmh_measurements_fail(self):
        source = self.root / 'jmh.json'
        source.write_text('[]')
        self.assertEqual(1, reports.jmh(source, self.output))

    def test_nonfinite_jmh_score_fails(self):
        item = {'benchmark':'decode', 'jvm':'java', 'jdkVersion':'21', 'jmhVersion':'1.37',
                'warmupIterations':3, 'measurementIterations':5, 'forks':2,
                'primaryMetric':{'score':float('nan'), 'scoreUnit':'ns/op'}}
        source = self.root / 'jmh.json'
        source.write_text(json.dumps([item] * 4))
        self.assertEqual(1, reports.jmh(source, self.output))

    def test_device_report_rejects_wrong_commit_and_no_workload(self):
        raw = self.root / 'raw'
        raw.mkdir()
        (raw / 'summary.json').write_text(json.dumps({'samples':10,'sampled_cpu_seconds':1}))
        (raw / 'metadata.json').write_text(json.dumps({'git_head':'old','recorded_actions':0}))
        with patch.dict('os.environ', {'CI_COMMIT_SHA':'new'}):
            self.assertEqual(1, reports.simpleperf(raw, self.output))
        self.assertGreaterEqual(int(ET.parse(self.output / 'junit.xml').getroot().get('failures')), 2)


    def test_regression_threshold_boundary_and_failure(self):
        checks = []
        source = {'pipeline': 19, 'commit': 'baseline'}
        reports.compare(checks, 'time', 120, 100, 20, 'ns/op', source)
        reports.compare(checks, 'time', 120.01, 100, 20, 'ns/op', source)
        self.assertEqual(['passed', 'failed'], [row[1] for row in checks])
        self.assertIn('delta=+20.01%', checks[1][2])
        self.assertEqual(1, reports.write_reports(self.output, 'regression', checks, {}))
        self.assertEqual('1', ET.parse(self.output / 'junit.xml').getroot().get('failures'))

    def test_real_baseline_passes_and_regressions_fail(self):
        base = reports.baseline()
        items = []
        for name, ref in base['jmh'].items():
            item = dict(ref['config'], benchmark=name, jvm='java',
                        primaryMetric={'score': ref['score'], 'scoreUnit': ref['unit']},
                        secondaryMetrics={'gc.alloc.rate.norm': {'score': ref['allocation']}})
            items.append(item)
        source = self.root / 'jmh.json'
        source.write_text(json.dumps(items))
        self.assertEqual(0, reports.jmh(source, self.output))
        items[0]['primaryMetric']['score'] *= 1.21
        source.write_text(json.dumps(items))
        self.assertEqual(1, reports.jmh(source, self.output))
        self.assertIn('time_regression', (self.output / 'junit.xml').read_text())
        items[0]['primaryMetric']['score'] /= 1.21
        items[-1]['secondaryMetrics']['gc.alloc.rate.norm']['score'] += 2
        source.write_text(json.dumps(items))
        self.assertEqual(1, reports.jmh(source, self.output))

    def test_device_baseline_passes_and_cpu_or_workload_regression_fails(self):
        base = reports.baseline()['simpleperf']
        raw = self.root / 'raw'
        raw.mkdir()
        summary = {'samples': 9411, 'sampled_cpu_seconds': base['cpu_seconds'], 'event': base['event']}
        metadata = dict(base['config'], recorded_actions=base['actions'], git_head='current', record_command=['-r', base['record_options']])
        import hashlib
        plan_bytes = (self.root / 'tools/profiling/dashboard-replay.json').read_bytes()
        plan = json.loads(plan_bytes)
        metadata.update(replay_mode='device-uptime-v1', replay_plan_sha256=hashlib.sha256(plan_bytes).hexdigest(), warmup_actions=31)
        log = []
        for phase in ('warmup', 'recording'):
            metadata[phase + '_replay'] = [dict(item, phase=phase, index=index, actual_seconds=item['seconds'], finished_seconds=item['seconds'] + 0.01) for index, item in enumerate(plan['phases'][phase])]
            log.extend(metadata[phase + '_replay'])
        (raw / 'actions.jsonl').write_text('\n'.join(json.dumps(item) for item in log))
        def run():
            (raw / 'summary.json').write_text(json.dumps(summary))
            (raw / 'metadata.json').write_text(json.dumps(metadata))
            with patch.dict('os.environ', {'CI_COMMIT_SHA':'current'}):
                return reports.simpleperf(raw, self.output)
        self.assertEqual(0, run())
        summary['sampled_cpu_seconds'] *= 1.21
        self.assertEqual(1, run())
        summary['sampled_cpu_seconds'] = base['cpu_seconds']
        metadata['recorded_actions'] = 80
        self.assertEqual(1, run())
        metadata['recorded_actions'] = base['actions']
        metadata['fingerprint'] = 'different device'
        self.assertEqual(1, run())

    def test_path_traversal_rejected(self):
        with self.assertRaises(ValueError):
            reports.relative_path('../outside.kt')


if __name__ == '__main__':
    unittest.main()
