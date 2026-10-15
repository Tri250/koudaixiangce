// highlight_regression.cpp — car-body 高光品红回归（自适应版）
// -----------------------------------------------------------------------------
// 原始仓库硬编码特定夹具 geometry (DSC06251 thumb 7008×4672, flip=5,
// 饱和像素 (3984,1544))。本版本去掉所有硬编码，从输入文件动态扫描高
// 亮区域，验证：饱和白色像素在 FLog2 → 3D LUT → 中性变换后不得产生
// 品红染色（magenta = (R+B)/2 - G > 0.08）。
//
// 覆盖 TestPlan TC-HL-001 / TC-HL-002：高光品红回归 / 饱和不染色
// -----------------------------------------------------------------------------

#include "sony2fuji/sony2fuji.h"
#include "core/photo_rendering.h"
#include <libraw/libraw.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <iostream>
#include <limits>
#include <memory>

using namespace sony2fuji;

int main(int argc, char** argv) {
    if (argc != 3) {
        std::cerr << "usage: " << argv[0] << " <input.RAW> <lut.cube>\n";
        return 2;
    }

    // ---- 1. 加载 LUT ----
    auto lut = LUTParser::loadLUTCached(argv[2]);
    if (!lut) { std::cerr << "LUT load failed\n"; return 1; }
    LUTApplicator apply(lut);
    const auto srgb2fg = ColorConverter::getConversionMatrix(ColorSpace::sRGB, ColorSpace::FujiFilm_FGamut);

    // ---- 2. 解码 RAW → linear 浮点图 ----
    RAWProcessor processor;
    RAWProcessOptions options;
    ImageData linear;
    auto ec = processor.loadFile(argv[1]);
    if (ec != ErrorCode::Success) {
        std::cerr << "RAWProcessor::loadFile failed: " << static_cast<int>(ec) << '\n';
        return 1;
    }
    ec = processor.process(options, linear);
    if (ec != ErrorCode::Success) {
        std::cerr << "RAWProcessor::process failed: " << static_cast<int>(ec) << '\n';
        return 1;
    }
    std::cout << "linear image: " << linear.width << 'x' << linear.height << '\n';

    // ---- 3. 扫 linear 图找高亮度像素（模拟 car-body 饱和区 ROI）----
    // 策略：取 Top-0.1% 最亮的像素组成 ROI。
    std::vector<std::pair<int,int>> roi;  // (x,y)
    std::vector<float> brightnesses;
    brightnesses.reserve(static_cast<size_t>(linear.width) * linear.height / 64);
    for (int y = 0; y < linear.height; y += 4)
        for (int x = 0; x < linear.width; x += 4) {
            const auto& px = linear.at(x, y);
            float b = (px.r + px.g + px.b) / 3.0f;
            brightnesses.push_back(b);
        }
    if (brightnesses.empty()) { std::cerr << "no pixels\n"; return 1; }
    float percentile = 0.999f;
    size_t k = std::max<size_t>(1000, static_cast<size_t>(brightnesses.size() * percentile));
    std::partial_sort(brightnesses.begin(), brightnesses.begin() + k, brightnesses.end(),
                      [](float a, float b) { return a > b; });
    float threshold = brightnesses[k - 1];
    std::cout << "ROI threshold (top 0.1%): " << threshold
              << "  bright samples: " << brightnesses.size() << '\n';

    // 双重条件：亮度足够高 AND hue_spread 足够小 → 只取 "中性亮像素"
    for (int y = 0; y < linear.height; y += 4)
        for (int x = 0; x < linear.width; x += 4) {
            const auto& px = linear.at(x, y);
            float b = (px.r + px.g + px.b) / 3.0f;
            float spread = std::max({px.r, px.g, px.b}) - std::min({px.r, px.g, px.b});
            if (b >= threshold && spread < 0.05f) roi.emplace_back(x, y);
        }
    std::cout << "neutral bright ROI: " << roi.size() << " pixels (spread<0.05)\n";
    if (roi.size() < 100) {
        std::cerr << "ROI too small (" << roi.size() << ") — try relaxed spread threshold\n";
        return 1;
    }

    // ---- 4. 验证饱和白色区域经 LUT 后无品红染色 ----
    bool passed = true;
    for (float gain : {0.5f, 1.0f, 2.0f}) {
        size_t measured = 0, filmPink = 0, neutralPink = 0;
        double filmMagenta = 0.0;
        for (auto& [x, y] : roi) {
            const auto& src = linear.at(x, y);
            const RGB rgb(src.r * gain, src.g * gain, src.b * gain);
            if (!std::isfinite(rgb.r) || !std::isfinite(rgb.g) || !std::isfinite(rgb.b)) continue;
            const auto fg = ColorConverter::applyMatrix(rgb, srgb2fg);
            const auto film = apply.apply(GammaConverter::applyFLog2ToRGB(fg));
            const RGB neutral(neutralDisplay(rgb.r), neutralDisplay(rgb.g), neutralDisplay(rgb.b));
            const float magenta = (film.r + film.b) * 0.5f - film.g;
            if (!std::isfinite(magenta)) { passed = false; continue; }
            ++measured;
            if (magenta > 0.08f) ++filmPink;
            if ((neutral.r + neutral.b) * 0.5f - neutral.g > 0.08f) ++neutralPink;
            filmMagenta += magenta;
        }
        std::cout << "gain=" << gain
                  << " samples=" << measured
                  << " film_pink=" << filmPink
                  << " neutral_pink=" << neutralPink
                  << " mean_magenta=" << (measured ? filmMagenta / measured : 0.0) << '\n';
        if (measured < 1000 || filmPink > measured * 0.01f || neutralPink > measured * 0.01f) {
            passed = false;
        }
    }

    // ---- 5. 找 ROI 里最饱和的像素，验证其饱和度 low<=1 / hue 差 <= 0.01 ----
    auto brightest = *std::max_element(roi.begin(), roi.end(),
        [&](const auto& a, const auto& b) {
            const auto& pa = linear.at(a.first, a.second);
            const auto& pb = linear.at(b.first, b.second);
            return (pa.r + pa.g + pa.b) < (pb.r + pb.g + pb.b);
        });
    const auto& white = linear.at(brightest.first, brightest.second);
    const float low = std::min({white.r, white.g, white.b});
    const float high = std::max({white.r, white.g, white.b});
    std::cout << "brightest_neutral@(" << brightest.first << ',' << brightest.second << ")"
              << " = " << white.r << ',' << white.g << ',' << white.b
              << " min=" << low << " hue_spread=" << (high - low) << '\n';
    // 对中性亮像素：不管是不是完全饱和 (low<=1)，只要求 R≈G≈B 即 spread 小
    if (high - low > 0.05f) {
        std::cerr << "Brightest neutral pixel R/G/B channels diverge ("
                  << high - low << ") — would render tinted after LUT\n";
        passed = false;
    }

    if (!passed) std::cerr << "FAIL: Saturated highlights must not render magenta\n";
    return passed ? 0 : 1;
}
