"""CI gate contracts: real image comparison and failure propagation."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
from PIL import Image

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("run_ci", HERE / "run_ci.py")
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)
SCENES = ["home_demo", "map_fullscreen", "media_queue", "home_connected",
          "vehicle_connected", "vehicle_disconnected"]


class ReportTests(unittest.TestCase):
    def test_exact_changed_missing_and_size_mismatch(self):
        for scenario in ["equal", "changed", "missing", "size"]:
            with self.subTest(scenario=scenario), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                before, after = root / "before", root / "after"
                before.mkdir()
                after.mkdir()
                for scene in SCENES:
                    Image.new("RGB", (4, 4), "black").save(before / f"{scene}.png")
                    Image.new("RGB", (4, 4), "black").save(after / f"{scene}.png")
                image_path = after / "home_demo.png"
                if scenario == "changed":
                    image = Image.open(image_path)
                    image.putpixel((0, 0), (255, 0, 0))
                    image.save(image_path)
                elif scenario == "missing":
                    image_path.unlink()
                elif scenario == "size":
                    Image.new("RGB", (2, 2)).save(image_path)
                manifest = root / "manifest.json"
                manifest.write_text('{"reference_commit":"fixture"}', encoding="utf-8")
                report = root / "report"
                result = subprocess.run([sys.executable, str(HERE / "report.py"),
                    "--baseline", str(before), "--current", str(after),
                    "--manifest", str(manifest), "--output", str(report)],
                    capture_output=True, check=False)
                self.assertEqual(result.returncode, 0 if scenario == "equal" else 1)
                self.assertTrue((report / "summary-zh.html").exists())
                data = json.loads((report / "pixel-results.json").read_text(encoding="utf-8"))
                self.assertEqual(data["incomplete_scenes"], int(scenario in ["missing", "size"]))
                if scenario == "changed":
                    self.assertEqual(data["scenes"][0]["changed_pixels"], 1)

    def test_runner_does_not_hide_gradle_failure(self):
        for exits in [(0, 0, 0), (1, 0, 0), (0, 1, 0), (0, 0, 1)]:
            with self.subTest(exits=exits), tempfile.TemporaryDirectory() as directory:
                with patch.object(runner, "ROOT", Path(directory)), \
                     patch.object(runner.sys, "platform", "win32"), \
                     patch.object(runner, "run_command", side_effect=exits) as command:
                    self.assertEqual(runner.main(), int(any(exits)))
                    self.assertEqual(command.call_count, 3)
                    capture = command.call_args_list[0].args[0]
                    self.assertTrue(any(item.startswith("-PhomeScreenshotDirectory=build/") for item in capture))
                    verify = command.call_args_list[1].args[0]
                    self.assertIn(":app:verifyRoborazziDebug", verify)
                    self.assertFalse(any(item.startswith("-PhomeScreenshotDirectory=") for item in verify))


if __name__ == "__main__":
    unittest.main()
