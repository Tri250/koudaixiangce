// =============================================================================
// contract_roundtrip_tests.cpp — ColorConverter / GammaConverter 数学契约
// -----------------------------------------------------------------------------
// 覆盖 TestPlan TC-CS-002 / 007 / 008 / 009 / 004。
// 纯数学测试，不依赖任何外部文件或 GPU。
// =============================================================================

#include "sony2fuji/sony2fuji.h"
#include <algorithm>
#include <array>
#include <cmath>
#include <iostream>

using namespace sony2fuji;

static int failures = 0;
static int total = 0;
static void check(bool ok, const char* name) {
    ++total;
    std::cout << (ok ? "PASS " : "FAIL ") << name << '\n';
    if (!ok) ++failures;
}
static float absf(float x) { return std::fabs(x); }

// -----------------------------------------------------------------------------
// TC-CS-007 — sRGB Gamma round-trip 1001 点
// -----------------------------------------------------------------------------
static void test_srgb_gamma_roundtrip() {
    bool ok = true;
    for (int i = 0; i <= 1000; ++i) {
        float linear = i / 1000.0f;
        float enc = GammaConverter::applySRGBGamma(linear);
        float dec = GammaConverter::removeSRGBGamma(enc);
        if (absf(dec - linear) >= 1e-4f) { ok = false; break; }
    }
    check(ok, "sRGB gamma round-trip 1001 点 err < 1e-4");
}

// -----------------------------------------------------------------------------
// TC-CS-008 — F-Log2 编码单调性 & 正区间
// -----------------------------------------------------------------------------
static void test_flog2_monotonic() {
    bool ok = true;
    float prev = -1e9f;
    for (int i = 0; i <= 1000; ++i) {
        float linear = (i / 1000.0f) * 16.0f;
        float enc = GammaConverter::applyFLog2(linear);
        if (enc <= prev) { ok = false; break; }
        prev = enc;
        if (enc < 0.0f) { ok = false; break; }
    }
    check(ok, "F-Log2 单调递增 & 正区间 [0,16]");
    float neg = GammaConverter::applyFLog2(-1.0f);
    check(!std::isnan(neg) && !std::isinf(neg), "F-Log2 on negative finite (clamped)");
}

// -----------------------------------------------------------------------------
// TC-CS-009 — F-Log2 diagnostics
// -----------------------------------------------------------------------------
static void test_flog2_diagnostics() {
    ImageData img;
    img.width = 4; img.height = 1;
    img.pixels = std::vector<RGB>(4);
    img.pixels[0] = {-2.0f, 0.0f, 0.0f};
    img.pixels[1] = {0.5f, 0.5f, 0.5f};
    img.pixels[2] = {10.0f, 10.0f, 10.0f};
    img.pixels[3] = {20.0f, 20.0f, 20.0f};

    auto d = GammaConverter::applyFLog2ToImage(img, {true});
    check(d.negative_pixels >= 1, "F-Log2 diagnostics 检测负像素");
    check(d.negative_pixels + d.over_pixels <= 4, "diagnostic counts ≤ total pixels");
    bool no_nan = true;
    for (auto& px : img.pixels)
        if (std::isnan(px.r) || std::isnan(px.g) || std::isnan(px.b)) no_nan = false;
    check(no_nan, "diagnostics 不污染原始像素");
}

// -----------------------------------------------------------------------------
// TC-CS-002 / TC-CS-003 — 色彩空间 round-trip（用 public invertMatrix）
// -----------------------------------------------------------------------------
static float roundtrip_error(const ColorConverter::Matrix3x3& fwd,
                              const RGB& in) {
    auto inv = ColorConverter::invertMatrix(fwd);
    RGB mid = ColorConverter::applyMatrix(in, fwd);
    RGB out = ColorConverter::applyMatrix(mid, inv);
    return std::max({ absf(in.r - out.r), absf(in.g - out.g), absf(in.b - out.b) });
}

static void test_colorspace_roundtrip(ColorSpace from, ColorSpace to, const char* label) {
    auto fwd = ColorConverter::getConversionMatrix(from, to);
    bool all_ok = true;
    for (float r : {0.0f, 0.2f, 0.5f, 0.8f, 1.0f})
        for (float g : {0.0f, 0.5f, 1.0f})
            for (float b : {0.0f, 0.5f, 1.0f})
                if (roundtrip_error(fwd, {r,g,b}) >= 1e-3f) all_ok = false;
    char name[128]; snprintf(name, sizeof(name), "round-trip %s < 1e-3", label);
    check(all_ok, name);
}

// -----------------------------------------------------------------------------
// TC-CS-004 — 所有 ColorSpace 互转矩阵有限且非奇异
// -----------------------------------------------------------------------------
static void test_all_pairs() {
    ColorSpace spaces[] = {
        ColorSpace::sRGB, ColorSpace::BT709, ColorSpace::ProPhotoRGB,
        ColorSpace::FujiFilm_FGamut, ColorSpace::ACEScg, ColorSpace::AdobeRGB,
        ColorSpace::ACES2065_1, 
    };
    bool all_finite = true;
    for (auto from : spaces)
        for (auto to : spaces) {
            auto m = ColorConverter::getConversionMatrix(from, to);
            for (auto& row : m) for (auto v : row)
                if (!std::isfinite(v)) all_finite = false;
        }
    check(all_finite, "所有 ColorSpace 对产出有限矩阵");

    bool det_ok = true;
    for (auto from : spaces)
        for (auto to : spaces) {
            auto m = ColorConverter::getConversionMatrix(from, to);
            float det = m[0][0]*(m[1][1]*m[2][2]-m[1][2]*m[2][1])
                      - m[0][1]*(m[1][0]*m[2][2]-m[1][2]*m[2][0])
                      + m[0][2]*(m[1][0]*m[2][1]-m[1][1]*m[2][0]);
            if (std::abs(det) < 1e-8f) det_ok = false;
        }
    check(det_ok, "所有转换矩阵非奇异（det ≠ 0）");
}

int main() {
    test_srgb_gamma_roundtrip();
    test_flog2_monotonic();
    test_flog2_diagnostics();
    test_colorspace_roundtrip(ColorSpace::sRGB, ColorSpace::ProPhotoRGB, "sRGB↔ProPhoto");
    test_colorspace_roundtrip(ColorSpace::FujiFilm_FGamut, ColorSpace::AdobeRGB, "F-Gamut↔AdobeRGB");
    test_all_pairs();

    std::cout << "\n===== Summary: " << (total - failures) << "/" << total
              << " PASS";
    if (failures) std::cout << "  ❌ " << failures << " FAIL";
    std::cout << " =====\n";
    return failures ? 1 : 0;
}
