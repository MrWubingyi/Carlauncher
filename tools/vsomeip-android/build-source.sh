#!/usr/bin/env bash
# Linux source build. Android CLI must install the pinned SDK components first.
set -euo pipefail
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$root"
sdk=${ANDROID_HOME:?Set ANDROID_HOME to the SDK managed by Android CLI}
ndk="$sdk/ndk/28.2.13676358"
cmake="$sdk/cmake/3.22.1/bin/cmake"
llvm="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
out="$root/build/native"
source_root="$root/third_party/vsomeip"
jobs=${NATIVE_BUILD_JOBS:-2}
boost_version=1.90.0
boost_archive=boost_1_90_0.tar.bz2
boost_sha256=49551aff3b22cbc5c5a9ed3dbc92f0e23ea50a0f7325b0d198b705e8ee3fc305

test -x "$cmake"
test -x "$llvm/x86_64-linux-android24-clang++"
expected=$(git ls-tree HEAD third_party/vsomeip | awk '{print $3}')
actual=$(git -C "$source_root" rev-parse HEAD)
test "$actual" = "$expected"
test -z "$(git -C "$source_root" status --porcelain --untracked-files=all)"
# Refuse stale outputs: every successful invocation proves a new source build.
if [[ -e "$out" ]]; then
  echo "Remove the generated build/native directory before rebuilding." >&2
  exit 1
fi
mkdir -p "$out/artifact/lib/x86_64" "$out/artifact/licenses" "$out/logs"
exec > >(tee "$out/logs/source-build.log") 2>&1
# Preserve configuration evidence even when configure/compile fails.
trap 'for f in CMakeCache.txt compile_commands.json; do
  if [[ -f "$out/vsomeip-build/$f" ]]; then cp "$out/vsomeip-build/$f" "$out/logs/"; fi
done' EXIT
curl --fail --location --retry 3 \
  "https://archives.boost.io/release/$boost_version/source/$boost_archive" \
  --output "$out/$boost_archive"
echo "$boost_sha256  $out/$boost_archive" | sha256sum --check
tar -xjf "$out/$boost_archive" -C "$out"
boost_source="$out/boost_1_90_0"
boost_prefix="$out/boost-install"
cat > "$out/user-config.jam" <<EOF
using clang : android : "$llvm/x86_64-linux-android24-clang++"
  : <archiver>"$llvm/llvm-ar" <ranlib>"$llvm/llvm-ranlib" ;
EOF
(
  cd "$boost_source"
  ./bootstrap.sh --with-libraries=filesystem
  ./b2 --user-config="$out/user-config.jam" toolset=clang-android \
    target-os=android architecture=x86 address-model=64 \
    link=static runtime-link=shared threading=multi variant=release \
    cxxstd=17 cxxflags=-fPIC --with-filesystem --layout=system \
    --prefix="$boost_prefix" -d0 -j"$jobs" install
)
"$cmake" -S "$source_root" -B "$out/vsomeip-build" -G Ninja \
  -DCMAKE_MAKE_PROGRAM="$sdk/cmake/3.22.1/bin/ninja" \
  -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=x86_64 -DANDROID_PLATFORM=android-24 -DANDROID_STL=c++_shared \
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
  -DCMAKE_FIND_ROOT_PATH="$boost_prefix" -DCMAKE_PREFIX_PATH="$boost_prefix" \
  -DBoost_DIR="$boost_prefix/lib/cmake/Boost-1.90.0" -DBoost_USE_STATIC_LIBS=ON \
  -DENABLE_MULTIPLE_ROUTING_MANAGERS=ON -DANDROID_CI_BUILD=ON \
  -DDISABLE_DLT=ON -DDISABLE_SYSTEMD=ON
"$cmake" --build "$out/vsomeip-build" --target vsomeip3 --parallel "$jobs"
library="$out/artifact/lib/x86_64/libvsomeip3.so"
"$llvm/llvm-strip" --strip-unneeded -o "$library" "$out/vsomeip-build/libvsomeip3.so"
"$llvm/llvm-readelf" -h -d "$library" > "$out/artifact/elf.txt"
"$llvm/llvm-nm" -D --defined-only "$library" > "$out/artifact/symbols.txt"
grep -q 'ELF64' "$out/artifact/elf.txt"
grep -q 'Advanced Micro Devices X86-64' "$out/artifact/elf.txt"
grep -q 'Library soname: \[libvsomeip3.so\]' "$out/artifact/elf.txt"
grep -q ' T vsomeip_android_set_network_state$' "$out/artifact/symbols.txt"
# Boost is statically linked; no host Linux libraries or vSomeIP plugins allowed.
python3 "$root/tools/vsomeip-android/verify-artifact.py" --elf "$out/artifact/elf.txt"
cp "$source_root/LICENSE" "$out/artifact/licenses/vsomeip-MPL-2.0.txt"
cp "$boost_source/LICENSE_1_0.txt" "$out/artifact/licenses/boost.txt"
export NATIVE_BOOST_SHA256="$boost_sha256"
python3 - "$root" "$actual" <<'PY'
import json, os, pathlib, subprocess, sys
root = pathlib.Path(sys.argv[1])
data = {
    "repository_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
    "vsomeip_commit": sys.argv[2], "boost_version": "1.90.0",
    "boost_source_sha256": os.environ["NATIVE_BOOST_SHA256"],
    "ndk": "28.2.13676358", "cmake": "3.22.1", "abi": "x86_64", "api": 24,
    "stl": "c++_shared", "configuration": "Release", "boost_linkage": "static",
    "multiple_routing_managers": True, "android_ci_build": True,
    "run_url": f'https://github.com/{os.getenv("GITHUB_REPOSITORY", "")}/actions/runs/{os.getenv("GITHUB_RUN_ID", "")}',
    "compiler": subprocess.check_output([os.environ["ANDROID_HOME"] + "/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++", "--version"], text=True).strip(),
}
(root / "build/native/artifact/provenance.json").write_text(json.dumps(data, indent=2) + "\n")
PY
(
  cd "$out/artifact"
  sha256sum lib/x86_64/libvsomeip3.so licenses/* provenance.json elf.txt symbols.txt > SHA256SUMS
  sha256sum --check SHA256SUMS
)
