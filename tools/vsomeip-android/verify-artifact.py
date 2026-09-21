"""Fail CI if a host dependency or a stale vSomeIP binary reaches the APK."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--elf", type=Path)
parser.add_argument("--apk", type=Path)
parser.add_argument("--artifact", type=Path, default=Path("build/native/artifact"))
args = parser.parse_args()
if not args.elf and not args.apk:
    parser.error("Specify --elf or --apk")
if args.elf:
    needed = set(re.findall(r"\(NEEDED\).*?\[(.*?)\]", args.elf.read_text()))
    allowed = {"libc++_shared.so", "liblog.so", "libc.so", "libm.so", "libdl.so"}
    if not needed or needed - allowed:
        raise SystemExit(f"Unexpected ELF dependencies: {sorted(needed)}")
    print(f"Android ELF dependencies: {sorted(needed)}")
if args.apk:
    member = "lib/x86_64/libvsomeip3.so"
    expected = hashlib.sha256((args.artifact / member).read_bytes()).hexdigest()
    with zipfile.ZipFile(args.apk) as apk:
        actual = hashlib.sha256(apk.read(member)).hexdigest()
    if actual != expected:
        raise SystemExit(f"APK library does not match this source build: {actual} != {expected}")
    result = {"apk": args.apk.name, "member": member, "sha256": actual, "matches_source_build": True}
    (args.artifact / "apk-verification.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result))
