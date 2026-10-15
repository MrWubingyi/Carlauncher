"""Verify weighting, recursion and safe offline embedding of sampled stacks."""
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
from flamegraph import add_stack, new_node, write_flamegraph


class FlameGraphTests(unittest.TestCase):
    def test_weighted_tree_keeps_recursive_frames_and_self_cost(self):
        tree = new_node('process')
        add_stack(tree, ['thread', 'recursive', 'recursive'], 20)
        add_stack(tree, ['thread', 'recursive'], 30)
        add_stack(tree, ['other'], 50)
        frame = tree['children']['thread']['children']['recursive']
        self.assertEqual(100, tree['value'])
        self.assertEqual(50, frame['value'])
        self.assertEqual(30, frame['self'])
        self.assertEqual(20, frame['children']['recursive']['self'])

    def test_html_is_offline_and_symbol_cannot_escape_script(self):
        tree = new_node('process')
        symbol = '</script><script>alert(1)</script>'
        add_stack(tree, [symbol], 1000000)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'flamegraph.html'
            write_flamegraph(path, tree)
            document = path.read_text(encoding='utf-8')
        self.assertNotIn(symbol, document)
        data = document.split('<script id="stacks" type="application/json">')[1].split('</script>')[0]
        self.assertEqual(symbol, json.loads(data)['children'][0]['name'])
        self.assertNotIn('<script src=', document)
        self.assertIn('Reset zoom', document)

    def test_no_samples_cannot_produce_a_valid_graph(self):
        with self.assertRaises(ValueError):
            write_flamegraph(Path('unused.html'), new_node('empty'))


if __name__ == '__main__':
    unittest.main()
