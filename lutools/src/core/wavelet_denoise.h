#pragma once
#include "sony2fuji/common.h"
#include "sony2fuji/gpu/lut_gpu.h"
#include <memory>
#include <string>

namespace sony2fuji {

struct WaveletDenoiseOptions {
    bool enabled = false;
    float luma = 40, chroma = 46, coarse = 50;
    bool active() const { return enabled && (luma > 0 || chroma > 0); }
    bool operator==(const WaveletDenoiseOptions& other) const {
        return enabled == other.enabled && luma == other.luma && chroma == other.chroma && coarse == other.coarse;
    }
};

struct WaveletDenoiseDiagnostics {
    int flatTiles = 0;
    bool applied = false;
    bool gpuUsed = false;
    bool analysisReused = false;
    bool coarseAnalysisReused = false;
    float a[3] = {}, b[3] = {};
};

class WaveletDenoiseAnalysisCache {
    struct Impl;
    std::unique_ptr<Impl> state;
    friend ErrorCode applyWaveletDenoise(ImageData&, const WaveletDenoiseOptions&,
        WaveletDenoiseDiagnostics*, GpuMode, WaveletDenoiseAnalysisCache*, const std::string&);
public:
    WaveletDenoiseAnalysisCache();
    ~WaveletDenoiseAnalysisCache();
    WaveletDenoiseAnalysisCache(WaveletDenoiseAnalysisCache&&) noexcept;
    WaveletDenoiseAnalysisCache& operator=(WaveletDenoiseAnalysisCache&&) noexcept;
    void clear();
};

bool waveletDenoiseAvailable();
bool validWaveletDenoiseOptions(const WaveletDenoiseOptions& options);
ErrorCode applyWaveletDenoise(ImageData& image, const WaveletDenoiseOptions& options,
                            WaveletDenoiseDiagnostics* diagnostics = nullptr, GpuMode gpuMode = GpuMode::Off,
                            WaveletDenoiseAnalysisCache* analysis = nullptr, const std::string& sourceKey = {});

} // namespace sony2fuji
