"""Fail CI if a host dependency or a stale vSomeIP binary reaches the APK."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import tarfile
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--elf", type=Path)
parser.add_argument("--apk", type=Path)
parser.add_argument("--artifact", type=Path, default=Path("build/native/artifact"))
parser.add_argument("--validate", action="store_true", help="Check checksums, provenance, ELF and symbols")
parser.add_argument("--repository-commit")
parser.add_argument("--vsomeip-commit")
parser.add_argument("--input-hash")
parser.add_argument("--extract", type=Path, help="Safely unpack an artifact archive before validation")
args = parser.parse_args()
if not args.elf and not args.apk and not args.validate:
    parser.error("Specify --elf, --apk or --validate")
if args.validate:
    root = args.artifact.resolve()
    required = {"lib/x86_64/libvsomeip3.so", "licenses/boost.txt", "licenses/vsomeip-MPL-2.0.txt",
                "provenance.json", "elf.txt", "symbols.txt"}
    if args.extract:
        if root.exists() and any(root.iterdir()):
            raise SystemExit("Artifact extraction directory must be empty")
        with tarfile.open(args.extract, "r:gz") as archive:
            seen = set()
            for member in archive.getmembers():
                name = member.name.removeprefix("./").rstrip("/")
                if member.isdir() and name in {".", "", "lib", "lib/x86_64", "licenses"}:
                    continue
                if not member.isfile() or name not in required | {"SHA256SUMS", "apk-verification.json"} or name in seen:
                    raise SystemExit(f"Unsafe artifact archive member: {member.name}")
                seen.add(name)
            for member in archive.getmembers():
                if member.isfile():
                    target = root / member.name.removeprefix("./")
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(archive.extractfile(member).read())
    checked = set()
    for line in (root / "SHA256SUMS").read_text().splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9_./-]+)", line)
        if not match:
            raise SystemExit("Malformed SHA256SUMS entry")
        expected, name = match.groups()
        target = (root / name).resolve()
        if not target.is_relative_to(root) or name in checked:
            raise SystemExit(f"Unsafe or duplicate checksum path: {name}")
        if hashlib.sha256(target.read_bytes()).hexdigest() != expected:
            raise SystemExit(f"Native artifact checksum mismatch: {name}")
        checked.add(name)
    if not required <= checked:
        raise SystemExit(f"Missing checksums: {sorted(required - checked)}")
    provenance = json.loads((root / "provenance.json").read_text())
    expected_fields = {"ndk": "28.2.13676358", "cmake": "3.22.1", "abi": "x86_64", "api": 24,
                       "stl": "c++_shared", "configuration": "Release", "boost_version": "1.90.0",
                       "boost_source_sha256": "49551aff3b22cbc5c5a9ed3dbc92f0e23ea50a0f7325b0d198b705e8ee3fc305",
                       "boost_linkage": "static", "multiple_routing_managers": True, "android_ci_build": True}
    for key, value in (("repository_commit", args.repository_commit),
                       ("vsomeip_commit", args.vsomeip_commit), ("native_input_sha256", args.input_hash)):
        if value is not None:
            expected_fields[key] = value
    for key, value in expected_fields.items():
        if provenance.get(key) != value:
            raise SystemExit(f"Native provenance mismatch: {key}")
    library = (root / "lib/x86_64/libvsomeip3.so").read_bytes()
    if library[:6] != b"\x7fELF\x02\x01" or library[18:20] != b"\x3e\x00":
        raise SystemExit("Native library is not ELF64 little-endian x86_64")
    elf = (root / "elf.txt").read_text()
    if "Library soname: [libvsomeip3.so]" not in elf:
        raise SystemExit("Unexpected vSomeIP SONAME")
    if not re.search(r" T vsomeip_android_set_network_state$", (root / "symbols.txt").read_text(), re.M):
        raise SystemExit("Missing Android network adapter symbol")
    args.elf = root / "elf.txt"
    print("Native artifact checksums and provenance verified")
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
