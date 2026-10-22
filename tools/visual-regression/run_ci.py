"""Run Windows golden verification and retain reports even when Gradle fails."""
import datetime
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
TEST = "com.zqautomotive.launcher.ui.screenshot.HomeScreenshotTest"


def run_command(command, log):
    try:
        with log.open("w", encoding="utf-8") as stream:
            return subprocess.run(command, cwd=ROOT, stdout=stream,
                                  stderr=subprocess.STDOUT, check=False).returncode
    except OSError as error:
        log.write_text(str(error), encoding="utf-8")
        return 127


def main():
    run_id = uuid.uuid4().hex
    output = ROOT / "build/ui-regression/ci" / run_id
    output.mkdir(parents=True)
    current_relative = f"build/outputs/home-ci-{run_id}"
    current = ROOT / "app" / current_relative
    result = {"started_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "commit": os.getenv("CI_COMMIT_SHA", os.getenv("GITHUB_SHA", "local-worktree")),
              "platform": platform.platform(), "python": platform.python_version()}
    # Each capture uses a fresh directory; a failed run cannot reuse old images.
    if sys.platform != "win32":
        result["error"] = "Windows baseline requires a Windows runner"
    else:
        started = time.time()
        gradle = [str(ROOT / "gradlew.bat"), "--no-daemon", "--stacktrace", "--rerun-tasks"]
        result["capture_exit"] = run_command(gradle + [":app:recordRoborazziDebug", "--tests", TEST,
            f"-PhomeScreenshotDirectory={current_relative}"], output / "capture.log")
        result["verify_exit"] = run_command(gradle + [":app:verifyRoborazziDebug", "--tests", TEST,
            "-Proborazzi.test.compare=true"], output / "verify.log")
        for source, target in [("app/build/reports/roborazzi", "roborazzi"),
                               ("app/build/outputs/roborazzi/home", "diffs"),
                               ("app/build/test-results/testDebugUnitTest", "junit"),
                               ("app/build/test-results/roborazzi", "results")]:
            for artifact in (ROOT / source).rglob("*"):
                if artifact.is_file() and artifact.stat().st_mtime >= started:
                    destination = output / target / artifact.relative_to(ROOT / source)
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(artifact, destination)
    (output / "run.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    report_exit = run_command([sys.executable, str(ROOT / "tools/visual-regression/report.py"),
        "--baseline", str(ROOT / "app/src/test/screenshots/home"),
        "--current", str(current), "--manifest",
        str(ROOT / "app/src/test/screenshots/home/baseline-provenance.json"),
        "--output", str(output)], output / "report.log")
    print(f"UI regression report: {output / 'summary-zh.html'}", flush=True)
    return 1 if report_exit or result.get("capture_exit", 1) or result.get("verify_exit", 1) else 0


if __name__ == "__main__":
    raise SystemExit(main())
