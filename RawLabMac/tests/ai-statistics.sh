#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$(mktemp -d /tmp/rawlab-ai-statistics.XXXXXX)"
trap 'rm -rf "$OUT"' EXIT
export DEVELOPER_DIR="${DEVELOPER_DIR:-/Library/Developer/CommandLineTools}"
SDK="${SDKROOT:-/Library/Developer/CommandLineTools/SDKs/MacOSX26.5.sdk}"
swiftc -parse-as-library -swift-version 5 -sdk "$SDK" -target "$(uname -m)-apple-macosx15.0" \
  "$ROOT/RawLabMac/tests/AIStatisticsTests.swift" "$ROOT/RawLabMac/Sources/AIColorRecipe.swift" \
  "$ROOT/RawLabMac/Sources/AIImage.swift" "$ROOT/RawLabMac/Sources/AIColorStatistics.swift" \
  -framework AppKit -framework ImageIO -o "$OUT/test"
"$OUT/test"
