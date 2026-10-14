#!/usr/bin/env bash
# Called by build-local.ps1. All inputs live in an isolated upload directory.
set -euo pipefail
base=$1
upload=$2
sdk=$3
commit=$4
source_commit=$5
input_hash=$6
jobs=$7
force=${8:-0}
export ANDROID_HOME="$sdk" ANDROID_SDK_ROOT="$sdk"
export PATH="$base/bin:$HOME/.local/bin:$PATH"
for tool in git curl tar bzip2 python3 g++; do
  command -v "$tool" >/dev/null || { echo "Missing Ubuntu prerequisite: $tool" >&2; exit 1; }
done
cache="$base/artifacts/$input_hash"
if [[ -f "$cache/native-artifact.tar.gz" && "$force" != 1 ]]; then
  if python3 "$upload/verify-artifact.py" --validate --artifact "$cache/artifact" \
      --repository-commit "$commit" --vsomeip-commit "$source_commit" --input-hash "$input_hash"; then
    # Repack validated files instead of trusting an independently cached archive.
    tar -czf "$upload/native-artifact.tar.gz" -C "$cache/artifact" .
    echo 'Reusing verified Ubuntu native artifact.'
    exit 0
  fi
  echo 'Ubuntu cache failed validation; rebuilding in a new directory.'
fi
if ! command -v android >/dev/null; then
  mkdir -p "$base/bin"
  curl --fail --location --retry 3 --retry-all-errors \
    https://dl.google.com/android/cli/latest/linux_x86_64/android --output "$base/bin/android"
  chmod +x "$base/bin/android"
fi
if [[ ! -x "$sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++" || \
      ! -x "$sdk/cmake/3.22.1/bin/cmake" ]]; then
  android --no-metrics --sdk="$sdk" sdk install ndk/28.2.13676358 cmake/3.22.1
fi
git clone --no-checkout "$upload/main.bundle" "$upload/project"
git -C "$upload/project" checkout --detach "$commit"
git clone --no-checkout "$upload/vsomeip.bundle" "$upload/project/third_party/vsomeip"
git -C "$upload/project/third_party/vsomeip" checkout --detach "$source_commit"
# Use the current local build scripts, including changes not committed yet.
cp "$upload/build-source.sh" "$upload/project/tools/vsomeip-android/build-source.sh"
cp "$upload/verify-artifact.py" "$upload/project/tools/vsomeip-android/verify-artifact.py"
export NATIVE_BUILD_JOBS="$jobs"
export NATIVE_DOWNLOAD_CACHE="$base/downloads"
bash "$upload/project/tools/vsomeip-android/build-source.sh"
artifact="$upload/project/build/native/artifact"
python3 - "$artifact/provenance.json" "$input_hash" <<'PY'
import json, pathlib, sys
path = pathlib.Path(sys.argv[1])
data = json.loads(path.read_text())
data['native_input_sha256'] = sys.argv[2]
data['run_url'] = None
path.write_text(json.dumps(data, indent=2) + '\n')
PY
(
  cd "$artifact"
  sha256sum lib/x86_64/libvsomeip3.so licenses/* provenance.json elf.txt symbols.txt > SHA256SUMS
)
python3 "$upload/verify-artifact.py" --validate --artifact "$artifact" \
  --repository-commit "$commit" --vsomeip-commit "$source_commit" --input-hash "$input_hash"
tar -czf "$upload/native-artifact.tar.gz" -C "$artifact" .
if [[ -e "$cache" ]]; then
  mv "$cache" "$cache-previous-$(basename "$upload")"
fi
mkdir -p "$cache"
cp -R "$artifact" "$cache/artifact"
cp "$upload/native-artifact.tar.gz" "$cache/native-artifact.tar.gz"
