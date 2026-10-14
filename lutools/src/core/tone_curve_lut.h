#pragma once

#include <array>
#include <cmath>
#include <cstddef>

namespace sony2fuji {

// Additive tone-curve LUT shared by the CPU and GLES photo paths. The 256
// entries map display-sRGB [0,1] onto [0,1]; active() gates the legacy
// request.tone_curve scalar, so both representations never stack.
struct ToneCurveLutOptions {
    std::array<float, 256> lut{};
    bool active = false;

    bool valid() const {
        if (!active) return true;
        for (float value : lut)
            if (!std::isfinite(value) || value < 0.0f || value > 1.0f) return false;
        return true;
    }
};

// Linear interpolation between neighbouring bins; mirrors the GLES
// texelFetch+mix shader exactly so CPU previews and GPU renders agree.
inline float sampleToneCurveLut(const ToneCurveLutOptions& options, float x) {
    if (!options.active) return x;
    float p = std::fmax(x, 0.0f);
    p = std::fmin(p, 1.0f) * 255.0f;
    int lo = static_cast<int>(p);
    if (lo > 255) lo = 255;
    const float t = p - static_cast<float>(lo);
    const int hi = lo < 255 ? lo + 1 : 255;
    const float a = options.lut[static_cast<size_t>(lo)];
    const float b = options.lut[static_cast<size_t>(hi)];
    return a + (b - a) * t;
}

} // namespace sony2fuji
