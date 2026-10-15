#!/usr/bin/env bash
# =============================================================================
# run_acceptance.sh — Linux (含 CI) / macOS 通用验收脚本
# -----------------------------------------------------------------------------
# 用法: bash ci/run_acceptance.sh [release|debug]
#   依赖: 已装好 libraw-dev / lcms2-dev / OpenMP / 可选 GPU framework
# 产物: build/ 二进制 + TestPlan 执行摘要
# =============================================================================
set -euo pipefail

MODE="${1:-release}"
SRC_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="$SRC_DIR/build"

echo "==> [1/5] 环境探测"
uname -a | head -1
cmake --version 2>/dev/null | head -1 || { echo "cmake not found"; exit 1; }
if [ "$(uname)" = "Darwin" ]; then
    echo "  macOS: Metal available"
    SONY2FUJI_ENABLE_GPU=ON
    LIBRAW_LIBS="-lraw -llcms2 -lz -lgomp -lpthread -lm"
else
    echo "  Linux: CPU-only (Metal/D3D11 需 macOS/Windows)"
    SONY2FUJI_ENABLE_GPU=OFF
fi

echo "==> [2/5] 配置 (SONY2FUJI_TEST_RAW=${SONY2FUJI_TEST_RAW:-auto-detect})"
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"
RAW_PATH="${SONY2FUJI_TEST_RAW:-}"
if [ -z "$RAW_PATH" ] || [ ! -e "$RAW_PATH" ]; then
    for cand in "$SRC_DIR/examples/DSC06251.ARW" \
                "$SRC_DIR/examples/sony_ilce_7m2_cc24_daylight.arw" \
                "$SRC_DIR/examples/*.arw"; do
        if [ -e "$cand" ]; then RAW_PATH="$cand"; break; fi
    done
fi
echo "  RAW fixture: ${RAW_PATH:-none found (skip raw_* tests)}"

cmake "$SRC_DIR" \
    -DCMAKE_BUILD_TYPE="${MODE^^}" \
    -DBUILD_TESTING=ON \
    -DBUILD_CLI=ON \
    -DBUILD_EXAMPLES=ON \
    -DSONY2FUJI_ENABLE_GPU="$SONY2FUJI_ENABLE_GPU" \
    -DSONY2FUJI_TEST_RAW="${RAW_PATH:-}" \
    2>&1 | grep -E "error:|SONY2FUJI|sony_highlights|dng_white|raw_" || true

echo "==> [3/5] 构建"
cmake --build . --config "${MODE^^}" -j"$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || 4)" 2>&1 | tail -5

echo "==> [4/5] CTest 全量跑"
ctest --output-on-failure -j1 2>&1 | tee /tmp/ctest_summary.txt
PASS=$(grep -oP '\d+(?=%)' /tmp/ctest_summary.txt | tail -1)
FAIL=$(grep -c Failed /tmp/ctest_summary.txt || true)
TOTAL=$(grep -oP '\d+(?= tests? passed)' /tmp/ctest_summary.txt | head -1 || echo "?")
echo "  摘要: ${PASS}% 通过, ${FAIL} 失败"
[ "$FAIL" -eq 0 ] || { echo "  ❌ 失败详情在 /tmp/ctest_summary.txt"; exit 2; }

echo "==> [5/5] CLI 端到端冒烟 (Sony ARW → ETERNA LUT → JPEG)"
SONY_ARW="${RAW_PATH:-$SRC_DIR/examples/DSC06251.ARW}"
ETERNA_LUT="$SRC_DIR/flog-2-new/FLog2_to_ETERNA_65grid_V.1.00.cube"
if [ -e "$SONY_ARW" ] && [ -e "$ETERNA_LUT" ]; then
    ./sony2fuji "$SONY_ARW" -o /tmp/acc_out.jpg -l "$ETERNA_LUT" -q 95 --camera-wb --exposure 0.0
    [ -s /tmp/acc_out.jpg ] && echo "  ✅ CLI 端到端输出 $(stat -c%s /tmp/acc_out.jpg 2>/dev/null || stat -f%z /tmp/acc_out.jpg) bytes" || \
        { echo "  ❌ CLI 输出为空"; exit 3; }
else
    echo "  ⚠️ 跳过 (RAW/LUT 夹具缺失)"
fi

echo ""
echo "================================="
echo "  ✅ ACCEPTANCE COMPLETE"
echo "  ctest: $PASS% passed, $FAIL failed"
echo "  logs : /tmp/ctest_summary.txt"
echo "================================="
