#!/usr/bin/env python3
"""Record the profiling APK's dashboard with the NDK's simpleperf (Python 3.10+)."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from replay import shell_script, parse_output, validate_results
from preflight import SCENARIO, check as check_preflight, device_config

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com.example.carlauncher.profiling"
ACTIVITY = PACKAGE + "/com.example.carlauncher.ui.home.ComposeMainActivity"


def run(command, **kwargs):
    return subprocess.check_output([str(x) for x in command], **kwargs)


def bounds(node):
    return tuple(map(int, re.findall(r"\d+", node.attrib["bounds"])))


def source_inputs(root):
    """Support both app-owned JNI and the optional extracted native module."""
    sources = [root / name for name in (
        "build.gradle.kts", "settings.gradle.kts", "gradle/libs.versions.toml",
        "app/build.gradle.kts", "someip-android/build.gradle.kts")]
    for name in ("app/src", "someip-android/src", "vehicle/native"):
        sources.extend((root / name).rglob("*"))
    return sorted(path for path in sources if path.is_file())


def native_symbols(root):
    module = "someip-android" if (root / "someip-android/build.gradle.kts").is_file() else "app"
    directory = root / module / "build/intermediates/cxx/RelWithDebInfo"
    return directory if directory.is_dir() else None


def dashboard_actions(xml):
    tree = ET.fromstring(xml)
    nodes = {n.get("content-desc"): n for n in tree.iter("node")
             if n.get("package") == PACKAGE and n.get("content-desc")}
    parents = {child: parent for parent in tree.iter() for child in parent}

    def tap(name):
        x1, y1, x2, y2 = bounds(nodes[name])
        return name, ["tap", (x1 + x2) // 2, (y1 + y2) // 2]

    play = "Play demo" if "Play demo" in nodes else "Pause demo"
    # Derive the exposed map area from actual semantics, above the media overlay.
    map_node = parents[parents[nodes["Zoom in"]]]
    left, top, right, bottom = bounds(map_node)
    bottom = min(bottom, bounds(nodes[play])[1] - 35)
    x1, x2 = int(left + (right - left) * .30), int(left + (right - left) * .65)
    y = int(top + (bottom - top) * .65)
    if right - left < 300 or bottom - top < 120:
        raise RuntimeError("Unsupported dashboard layout; inspect ui-before.xml")
    sx1, sy1, sx2, sy2 = bounds(nodes["Seek demo track"])
    sy = (sy1 + sy2) // 2
    actions = [tap("Zoom in"), tap("Zoom in"),
               ("Pan map right", ["swipe", x1, y, x2, y, 500]),
               ("Pan map left", ["swipe", x2, y, x1, y + 15, 500]),
               tap("Zoom out"), tap("Recenter map"), tap("Next track"),
               ("Toggle demo playback", tap(play)[1]),
               ("Seek demo track", ["swipe", sx1 + (sx2 - sx1) // 4, sy,
                                    sx1 + 3 * (sx2 - sx1) // 4, sy, 400]),
               tap("Previous track")]
    return actions


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--ndk", type=Path, required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--duration", type=int, default=60)
    parser.add_argument("--frequency", type=int, default=1000)
    parser.add_argument("--warmup", type=int, default=15)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--replay-plan", type=Path, help="Fixed baseline device-side action schedule")
    parser.add_argument("--baseline", type=Path, help="Require exact device/scenario match before warmup")
    args = parser.parse_args()
    if args.duration < 10 or args.warmup < 0 or not 1 <= args.frequency <= 1000:
        parser.error("duration >= 10, warmup >= 0 and frequency in 1..1000 required")
    sdk, ndk = args.sdk.resolve(), args.ndk.resolve()
    adb = sdk / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
    profiler = ndk / "simpleperf/app_profiler.py"
    apk = ROOT / "app/build/outputs/apk/profiling/app-profiling.apk"
    for path in (adb, profiler, apk):
        if not path.is_file():
            parser.error(f"Missing {path}; build :app:assembleProfiling first")
    output = (args.output or ROOT / "app/build/reports/simpleperf" /
              datetime.now().strftime("%Y%m%d-%H%M%S-dashboard")).resolve()
    output.mkdir(parents=True, exist_ok=False)
    env = dict(os.environ, ANDROID_SERIAL=args.serial,
               PATH=str(adb.parent) + os.pathsep + os.environ.get("PATH", ""))

    def device(*command):
        return run([adb, "-s", args.serial, *command], env=env, timeout=30).decode("utf-8", "replace").strip()

    base = json.loads(args.baseline.read_text(encoding='utf-8')) if args.baseline else None
    if base:
        ref = base['simpleperf']
        expected = {k: v for k, v in ref['config'].items() if k != 'action_plan'}
        expected.update(event=ref['event'], record_options=ref['record_options'])
        actual = device_config(device, args.serial, args.duration, args.warmup, args.frequency)
        check_preflight(expected, actual, output, 'device_and_options')

    if int(device("shell", "getprop", "ro.build.version.sdk")) < 29:
        raise RuntimeError("Non-debuggable profileable builds require Android 10+")
    existing = subprocess.run([str(adb), "-s", args.serial, "shell", "pidof", "simpleperf"],
                              capture_output=True, env=env)
    if existing.returncode == 0 and existing.stdout.strip():
        raise RuntimeError("Another simpleperf recording is running; leave it untouched")
    device("install", "-r", str(apk))
    device("shell", "am", "force-stop", PACKAGE)
    device("shell", "am", "start", "-W", "-n", ACTIVITY)
    device("shell", "uiautomator", "dump", "/sdcard/carlauncher-profile-ui.xml")
    xml = device("shell", "cat", "/sdcard/carlauncher-profile-ui.xml")
    (output / "ui-before.xml").write_text(xml, encoding="utf-8")
    try:
        actions = dashboard_actions(xml)
    except (KeyError, RuntimeError, ET.ParseError) as error:
        if base:
            check_preflight({'action_plan': base['simpleperf']['config']['action_plan']},
                            {'action_plan': f'UNAVAILABLE: {error}'}, output, 'live_dashboard')
        raise
    metadata = {
        "started_utc": datetime.now(timezone.utc).isoformat(),
        "package": PACKAGE, "serial": args.serial,
        "fingerprint": device("shell", "getprop", "ro.build.fingerprint"),
        "screen": device("shell", "wm", "size"),
        "device_user": device("shell", "am", "get-current-user"),
        "shell_id": device("shell", "id"),
        "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "git_head": run(["git", "rev-parse", "HEAD"], cwd=ROOT).decode().strip(),
        "worktree_status": run(["git", "status", "--short", "--ignore-submodules=all"], cwd=ROOT).decode(),
        "duration_seconds": args.duration, "warmup_seconds": args.warmup,
        "scenario": SCENARIO,
        "limitations": "Device-specific user-space CPU only, no real media decoder or vehicle stream; JIT enabled, R8 disabled",
        "action_plan": actions,
    }
    replay = None
    if base:
        check_preflight(base['simpleperf']['config'], metadata, output, 'live_dashboard')
        plan = json.loads(args.replay_plan.read_text(encoding='utf-8')) if args.replay_plan else {}
        check_preflight({'replay_source_commit': base['source']['commit']},
                        {'replay_source_commit': plan.get('source_commit')}, output, 'replay_provenance')
    if args.replay_plan:
        plan_bytes = args.replay_plan.read_bytes()
        replay = json.loads(plan_bytes)
        if args.duration != 60 or args.warmup != 15:
            raise ValueError('Baseline replay requires duration=60 and warmup=15')
        expected_schedule, actual_schedule = {}, {}
        for phase in ('warmup', 'recording'):
            for index, entry in enumerate(replay['phases'][phase]):
                name, command = actions[index % len(actions)]
                key = f'{phase}[{index}]'
                expected_schedule[key] = {'action': name, 'input': command}
                actual_schedule[key] = {'action': entry['action'], 'input': entry['input']}
        if base:
            check_preflight(expected_schedule, actual_schedule, output, 'replay_schedule')
        else:
            for key, value in expected_schedule.items():
                if actual_schedule[key] != value:
                    raise ValueError('Live dashboard does not match baseline action schedule')
        metadata['replay_mode'] = 'device-uptime-v1'
        metadata['replay_plan_sha256'] = hashlib.sha256(plan_bytes).hexdigest()
    sources = source_inputs(ROOT)
    metadata["source_sha256"] = {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                                 for p in sorted(sources)}
    (output / "worktree.diff").write_bytes(run([
        "git", "diff", "HEAD", "--", "app", "someip-android", "vehicle/native",
        "build.gradle.kts", "settings.gradle.kts", "gradle/libs.versions.toml"], cwd=ROOT))
    action_log = output / "actions.jsonl"

    def workload(seconds, phase, process=None):
        if replay is not None:
            entries = replay['phases'][phase]
            local_script = output / f'{phase}-replay.sh'
            local_script.write_text(shell_script(entries, seconds), encoding='utf-8', newline='\n')
            remote = f'/data/local/tmp/carlauncher-{phase}-replay.sh'
            device('push', str(local_script), remote)
            result = subprocess.run([str(adb), '-s', args.serial, 'shell', 'sh', remote],
                                    env=env, capture_output=True, text=True, encoding='utf-8',
                                    errors='replace', timeout=seconds + 15)
            (output / f'{phase}-replay.log').write_text(result.stdout + result.stderr, encoding='utf-8')
            completed = parse_output(result.stdout, entries, phase)
            with action_log.open('a', encoding='utf-8') as log:
                for entry in completed:
                    log.write(json.dumps(entry) + '\n')
            metadata[phase + '_replay'] = completed
            metadata['warmup_actions' if phase == 'warmup' else 'recorded_actions'] = len(completed)
            if result.returncode:
                raise RuntimeError(f'Device replay failed: {result.stderr}')
            validate_results(completed, entries, seconds if phase == 'recording' else None)
            return len(completed)
        start = time.monotonic()
        index = 0
        with action_log.open("a", encoding="utf-8") as log:
            while time.monotonic() - start < seconds:
                if process is not None and process.poll() is not None:
                    break
                name, command = actions[index % len(actions)]
                timestamp = time.monotonic() - start
                device("shell", "input", *map(str, command))
                log.write(json.dumps({"phase": phase, "seconds": round(timestamp, 3),
                                      "action": name, "input": command}) + "\n")
                log.flush()
                index += 1
                time.sleep(.25)
        return index

    print(f"Warmup {args.warmup}s; output: {output}", flush=True)
    metadata["warmup_actions"] = workload(args.warmup, "warmup")
    options = f"-e task-clock:u -f {args.frequency} -g --duration {args.duration}"
    command = [sys.executable, str(profiler), "-p", PACKAGE, "-r", options,
               "--disable_adb_root", "--ndk_path", str(ndk)]
    symbols = native_symbols(ROOT)
    if symbols is not None:
        command += ["-lib", str(symbols)]
    metadata["record_command"] = command
    metadata_path = output / "metadata.json"
    metadata_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    with (output / "record.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, cwd=output, env=env, stdout=log, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 90
            while "run adb cmd:" not in (output / "record.log").read_text(encoding="utf-8"):
                if process.poll() is not None or time.monotonic() > deadline:
                    raise RuntimeError(f"Recorder did not start; inspect {output / 'record.log'}")
                time.sleep(.2)
            # Give the spawned recorder time to attach; setup time is not workload time.
            time.sleep(1)
            print(f"Recording {args.duration}s of dashboard interaction", flush=True)
            metadata["recorded_actions"] = workload(args.duration - 1, "recording", process)
            code = process.wait(timeout=180)
            if code:
                raise RuntimeError(f"simpleperf failed ({code}); inspect record.log")
        finally:
            if process.poll() is None:
                # The device recorder has a bounded --duration; don't kill other sessions.
                process.terminate()
                process.wait(timeout=10)
            metadata["finished_utc"] = datetime.now(timezone.utc).isoformat()
            metadata_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    # Multi-display AAOS can print a warning to stdout before PNG bytes.
    displays = device("shell", "dumpsys", "SurfaceFlinger", "--display-id")
    primary = re.search(r"Display (\d+) \(HWC display 0\)", displays)
    display_args = ["-d", primary.group(1)] if primary else []
    device("shell", "screencap", *display_args, "-p", "/sdcard/carlauncher-profile-screen.png")
    device("pull", "/sdcard/carlauncher-profile-screen.png", str(output / "screen-after.png"))
    device("shell", "uiautomator", "dump", "/sdcard/carlauncher-profile-ui.xml")
    (output / "ui-after.xml").write_text(device("shell", "cat", "/sdcard/carlauncher-profile-ui.xml"), encoding="utf-8")
    report_command = [sys.executable, str(ndk / "simpleperf/report_html.py"), "--no_browser"]
    with (output / "report.log").open("w", encoding="utf-8") as log:
        subprocess.run(report_command, cwd=output, env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
    print(f"Finished: {output / 'report.html'}", flush=True)


if __name__ == "__main__":
    main()
