"""Negative checks for the local build's cache and provenance boundary."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest

VERIFIER = Path(__file__).with_name("verify-artifact.py")


class ArtifactValidationTest(unittest.TestCase):
    def setUp(self):
        test_root = Path(__file__).resolve().parents[2] / "build" / "artifact-validation-tests"
        test_root.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=test_root)
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "lib/x86_64").mkdir(parents=True)
        (self.root / "licenses").mkdir()
        header = bytearray(64)
        header[:6] = b"\x7fELF\x02\x01"
        header[18:20] = b"\x3e\x00"
        (self.root / "lib/x86_64/libvsomeip3.so").write_bytes(header)
        for name in ("boost.txt", "vsomeip-MPL-2.0.txt"):
            (self.root / "licenses" / name).write_text("license")
        (self.root / "elf.txt").write_text("Library soname: [libvsomeip3.so]\n(NEEDED) [libc.so]\n")
        (self.root / "symbols.txt").write_text("0001 T vsomeip_android_set_network_state\n")
        (self.root / "provenance.json").write_text(json.dumps({
            "repository_commit": "a" * 40, "vsomeip_commit": "b" * 40, "native_input_sha256": "c" * 64,
            "ndk": "28.2.13676358", "cmake": "3.22.1", "abi": "x86_64", "api": 24,
            "stl": "c++_shared", "configuration": "Release", "boost_version": "1.90.0",
            "boost_source_sha256": "49551aff3b22cbc5c5a9ed3dbc92f0e23ea50a0f7325b0d198b705e8ee3fc305",
            "boost_linkage": "static", "multiple_routing_managers": True, "android_ci_build": True,
        }))
        self.checksums()

    def checksums(self):
        files = sorted(p for p in self.root.rglob("*") if p.is_file() and p.name != "SHA256SUMS")
        (self.root / "SHA256SUMS").write_text("".join(
            f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(self.root).as_posix()}\n" for p in files))

    def run_verifier(self, *extra):
        return subprocess.run([sys.executable, str(VERIFIER), "--validate", "--artifact", str(self.root),
            "--repository-commit", "a" * 40, "--vsomeip-commit", "b" * 40, "--input-hash", "c" * 64,
            *extra], capture_output=True, text=True)

    def test_matching_artifact(self):
        self.assertEqual(self.run_verifier().returncode, 0)

    def test_changed_library(self):
        (self.root / "lib/x86_64/libvsomeip3.so").write_bytes(b"changed")
        self.assertIn("checksum mismatch", self.run_verifier().stderr)

    def test_stale_input_hash(self):
        self.assertIn("native_input_sha256", self.run_verifier("--input-hash", "d" * 64).stderr)

    def test_wrong_source_commit(self):
        self.assertIn("vsomeip_commit", self.run_verifier("--vsomeip-commit", "d" * 40).stderr)

    def test_host_library_dependency(self):
        (self.root / "elf.txt").write_text("Library soname: [libvsomeip3.so]\n(NEEDED) [libpthread.so.0]\n")
        self.checksums()
        self.assertIn("Unexpected ELF dependencies", self.run_verifier().stderr)

    def test_traversal_checksum_path(self):
        with (self.root / "SHA256SUMS").open("a") as stream:
            stream.write("0" * 64 + "  ../outside\n")
        self.assertIn("Unsafe", self.run_verifier().stderr)

    def test_missing_required_checksum(self):
        sums = self.root / "SHA256SUMS"
        sums.write_text("\n".join(line for line in sums.read_text().splitlines() if "licenses/boost.txt" not in line))
        self.assertIn("Missing checksums", self.run_verifier().stderr)

    def test_archive_symlink_rejected_before_extraction(self):
        archive = self.root / "unsafe.tar.gz"
        with tarfile.open(archive, "w:gz") as stream:
            member = tarfile.TarInfo("./licenses/boost.txt")
            member.type = tarfile.SYMTYPE
            member.linkname = "../../outside"
            stream.addfile(member)
        stage = self.root / "stage"
        result = self.run_verifier("--artifact", str(stage), "--extract", str(archive))
        self.assertIn("Unsafe artifact archive member", result.stderr)
        self.assertFalse(stage.exists())

    def test_archive_round_trip(self):
        archive = self.root / "valid.tar.gz"
        with tarfile.open(archive, "w:gz") as stream:
            for path in sorted(self.root.rglob("*")):
                if path.is_file() and path != archive:
                    stream.add(path, arcname="./" + path.relative_to(self.root).as_posix())
        result = self.run_verifier("--artifact", str(self.root / "stage"), "--extract", str(archive))
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
