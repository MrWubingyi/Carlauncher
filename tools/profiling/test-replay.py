"""Device replay must not hide dropped, late, failed or reordered actions."""
import json
from pathlib import Path
import sys
import unittest

sys.dont_write_bytecode = True
from replay import shell_script, parse_output, validate_results


class ReplayTests(unittest.TestCase):
    def setUp(self):
        self.plan = json.loads(Path(__file__).with_name('dashboard-replay.json').read_text())

    def test_original_baseline_schedule_is_fixed(self):
        self.assertEqual(31, len(self.plan['phases']['warmup']))
        entries = self.plan['phases']['recording']
        self.assertEqual(119, len(entries))
        script = shell_script(entries, 59)
        self.assertEqual(119, sum(line.startswith('input ') for line in script.splitlines()))
        self.assertIn('/proc/uptime', script)
        self.assertIn('set -eu', script)

    def test_completed_actions_are_checked_against_schedule(self):
        entries = self.plan['phases']['recording']
        output = '\n'.join(f'REPLAY {i} {round(e["seconds"] * 100)} {round(e["seconds"] * 100) + 1}' for i, e in enumerate(entries))
        result = parse_output(output, entries, 'recording')
        validate_results(result, entries, 59)
        with self.assertRaises(ValueError):
            validate_results(result[:-1], entries, 59)
        result[-1]['finished_seconds'] = 60
        with self.assertRaises(ValueError):
            validate_results(result, entries, 59)

    def test_duplicate_and_injected_commands_are_rejected(self):
        entries = self.plan['phases']['recording']
        with self.assertRaises(ValueError):
            parse_output('REPLAY 1 0 1', entries, 'recording')
        with self.assertRaises(ValueError):
            shell_script([{'seconds': 0, 'input': ['tap', '1; exit 0', 2]}], 59)


if __name__ == '__main__':
    unittest.main()
