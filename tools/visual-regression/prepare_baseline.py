"""Create a local source snapshot; never checkout/reset/stash the user's worktree."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
REPLACED = [
    "app/src/main/java/com/example/carlauncher/ui/home/ComposeMainActivity.kt",
    "app/src/main/java/com/example/carlauncher/ui/compose/CarLauncherScreen.kt",
    "app/src/main/java/com/example/carlauncher/ui/compose/CarLauncherViewModel.kt",
    "app/src/main/java/com/example/carlauncher/ui/compose/components/MapCard.kt",
]
ADDED = [
    "app/src/main/java/com/example/carlauncher/ui/compose/CarLauncherUiState.kt",
    "app/src/main/java/com/example/carlauncher/ui/home/HomeLayout.kt",
    "app/src/main/java/com/example/carlauncher/ui/home/cards",
    "app/src/main/java/com/example/carlauncher/ui/home/host",
    "app/src/main/java/com/example/carlauncher/ui/home/map",
    "app/src/test/java/com/example/carlauncher/ui/compose/CarLauncherHostTest.kt",
    "app/src/test/java/com/example/carlauncher/ui/home/cards/MediaCardPresenterTest.kt",
]


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ref", required=True, help="Commit containing the four pre-refactor files")
    parser.add_argument("--output", default="build/ui-regression/before")
    args = parser.parse_args()
    output = (ROOT / args.output).resolve()
    if not output.is_relative_to((ROOT / "build").resolve()) or output.exists():
        raise SystemExit("Output must be a NEW directory beneath this repository's build/")
    commit = git("rev-parse", "--verify", f"{args.ref}^{{commit}}").decode().strip()
    # Read all old blobs before creating the snapshot, so an invalid ref leaves no partial copy.
    old = {name: git("show", f"{commit}:{name}") for name in REPLACED}
    for name in ADDED:
        if git("ls-tree", "-r", "--name-only", commit, "--", name).strip():
            raise SystemExit(f"Ref already contains refactor addition: {name}")
    folders = ["app/src", "someip-android/src", "gradle", "config"]
    files = ["build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew",
             "gradlew.bat", "local.properties", "app/build.gradle.kts", "app/proguard-rules.pro",
             "someip-android/build.gradle.kts", "someip-android/consumer-rules.pro"]
    selected = set(files)
    for folder in folders:
        selected.update(p.relative_to(ROOT).as_posix() for p in (ROOT / folder).rglob("*") if p.is_file())
    entries = {}
    for name in sorted(selected):
        source = ROOT / name
        if not source.is_file():
            continue
        if any(name == addition or name.startswith(addition + "/") for addition in ADDED):
            continue
        current = source.read_bytes()
        baseline = old.get(name, current)
        target = output / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(baseline)
        # Paths/hashes only; local.properties is a local SDK setting, never copied to reports/git.
        if name != "local.properties":
            entries[name] = {"current_sha256": sha(current), "baseline_sha256": sha(baseline),
                             "origin": commit if name in old else "current-worktree-shared"}
    manifest = {"reference_commit": commit, "replaced": REPLACED, "omitted_refactor_additions": ADDED,
                "files": entries,
                "note": "Only the four refactored files come from Git. All other inputs, including "
                        "the clock seam, screenshot fixtures and unrelated edits, are shared."}
    (output / "source-manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    shutil.copy2(output / "source-manifest.json", output.parent / f"{output.name}-source-manifest.json")
    print(f"Prepared {len(entries)} shared/overlaid inputs in {output}")
    print(f"Pre-refactor blobs: {commit}")


if __name__ == "__main__":
    main()
