#!/usr/bin/env bash
set -euo pipefail
if [[ $# -lt 1 || $# -gt 2 ]]; then
    printf 'Usage: bash %s SDK_ROOT [OUTPUT_DIRECTORY]\n' "$0" >&2
    exit 2
fi
if [[ "$(uname -s)" != Darwin ]]; then
    printf 'This SDK 1.4 build helper currently supports macOS only.\n' >&2
    exit 2
fi
sdk="$1/source"
if [[ ! -f "$sdk/dng_validate.cpp" ]]; then
    printf 'Missing Adobe DNG SDK 1.4 source: %s\n' "$sdk" >&2
    exit 2
fi
out="${2:-$(cd "$(dirname "$0")" && pwd)/output/sdk-validation}"
mkdir -p "$out"
files=()
for source in "$sdk"/*.cpp; do
    case "$source" in
        */dng_xmp_sdk.cpp) ;;
        *) files+=("$source") ;;
    esac
done
clang++ -std=c++17 -O2 -I"$sdk" \
    -DUNIX_ENV=1 -DqDNGBigEndian=0 -DqDNGThreadSafe=1 \
    -DqDNGUseLibJPEG=0 -DqDNGUseXMP=0 \
    -DqDNGValidateTarget=1 -DqDNGValidate=1 -DqDNG64Bit=1 \
    -Wno-deprecated-declarations \
    "${files[@]}" -lz -framework Carbon -framework CoreFoundation -o "$out/dng_validate"
