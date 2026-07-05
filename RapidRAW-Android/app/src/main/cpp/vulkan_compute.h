#pragma once

#include <vulkan/vulkan.h>
#include <cstdint>
#include <atomic>
#include <string>

// Maximum number of curve control points supported
static constexpr int MAX_CURVE_POINTS = 12;

// HSL channel adjustment
// NOTE: _pad makes this 16 bytes so it matches a `vec4` member in the GLSL
// std140 uniform block (x=hue, y=saturation, z=luminance, w=pad). Without the
// pad the C++ struct would be 12 bytes and the GPU would read every HSL slot
// from the wrong offset.
struct HslChannel {
    float hue;
    float saturation;
    float luminance;
    float _pad;
};

// Color grading region (hue/sat/lum for shadows, midtones, highlights)
// Same 16-byte padding rationale as HslChannel above (matches GLSL vec4).
struct ColorGradingRegion {
    float hue;
    float saturation;
    float luminance;
    float _pad;
};

// All adjustment parameters packed for GPU upload
// Must match the compute shader's uniform block layout exactly
struct AdjustmentsUBO {
    // ── Image dimensions ─────────────────────────────────────
    float width;
    float height;
    float pad0[2];

    // ── Exposure & Brightness ────────────────────────────────
    float exposure;       // -5.0 .. 5.0
    float brightness;     // -1.0 .. 1.0
    float pad1[2];

    // ── White Balance ────────────────────────────────────────
    float temperature;    // -1.0 .. 1.0 (normalized from -100..100)
    float tint;           // -1.0 .. 1.0 (normalized from -100..100)
    float pad2[2];

    // ── Tonal Controls ───────────────────────────────────────
    float contrast;       // -1.0 .. 1.0
    float highlights;     // -1.0 .. 1.0
    float shadows;        // -1.0 .. 1.0
    float whites;         // -1.0 .. 1.0
    float blacks;         // -1.0 .. 1.0
    float clarity;        // -1.0 .. 1.0
    float centre;         // -1.0 .. 1.0
    float pad3;

    // ── Color ────────────────────────────────────────────────
    float saturation;     // -1.0 .. 1.0
    float vibrance;       // -1.0 .. 1.0
    float pad4[2];

    // ── HSL 8-Color Panel ────────────────────────────────────
    HslChannel hslRed;
    HslChannel hslOrange;
    HslChannel hslYellow;
    HslChannel hslGreen;
    HslChannel hslAqua;
    HslChannel hslBlue;
    HslChannel hslPurple;
    HslChannel hslMagenta;

    // ── Tone Curve (10 control points, interleaved x,y to match
    //    shader's `vec4 curvePoints[5]` which packs as (x0,y0,x1,y1)) ──
    //    Previous layout used `float curveX[10]; float curveY[10];` (X block
    //    then Y block), which mismatched the shader's interleaved layout and
    //    caused the GPU to read completely wrong curve control points.
    //    This struct packs each point as {x, y} so the binary layout is
    //    x0,y0,x1,y1,...,x9,y9 — identical to vec4 curvePoints[5] in GLSL.
    struct CurvePoint {
        float x;
        float y;
    } curvePoints[10];

    // ── Color Grading ────────────────────────────────────────
    ColorGradingRegion cgShadows;
    ColorGradingRegion cgMidtones;
    ColorGradingRegion cgHighlights;
    float cgBlend;        // 0.0 .. 1.0
    float cgBalance;      // -1.0 .. 1.0
    // Matches GLSL `vec2 padCG`. Without this the C++ struct is 8 bytes short
    // of the std140 layout, shifting every following field.
    float padCG[2];

    // ── CDL Lift/Gamma/Gain Per-Channel ──────────────────────
    // 9 individual floats + 3 individual pad floats = 48 bytes (3 vec4 rows).
    // Declared as individual floats (NOT float[3]) because a std140
    // `float[N]` array has a 16-byte stride and would not match this layout.
    float cdlShadowsR;
    float cdlShadowsG;
    float cdlShadowsB;
    float cdlMidtonesR;
    float cdlMidtonesG;
    float cdlMidtonesB;
    float cdlHighlightsR;
    float cdlHighlightsG;
    float cdlHighlightsB;
    float pad5a;
    float pad5b;
    float pad5c;

    // ── Vignette ─────────────────────────────────────────────
    float vignetteAmount;     // -1.0 .. 1.0
    float vignetteMidpoint;   // 0.0 .. 1.0
    float vignetteRoundness;  // -1.0 .. 1.0
    float vignetteFeather;    // 0.0 .. 1.0

    // ── Film Grain ───────────────────────────────────────────
    float grainAmount;        // 0.0 .. 1.0
    float grainSize;          // 0.5 .. 3.0
    float grainRoughness;     // 0.0 .. 1.0
    float pad6;

    // ── Tone Mapping ─────────────────────────────────────────
    int32_t toneMapMode;      // 0=AgX, 1=ACES(full RRT+ODT), 2=Filmic, 3=OpenDRT
    float agxContrast;        // 0.0 .. 1.0
    float agxPedestal;        // 0.0 .. 0.5
    float pad7;

    // ── Sharpening ───────────────────────────────────────────
    float sharpness;          // 0.0 .. 4.0
    float sharpenRadius;      // 0.5 .. 3.0
    float pad8[2];

    // ════════════════════════════════════════════════════════════════════════
    // ── EXTENDED OPERATIONS (B1-B12) ────────────────────────────────────────
    // Every group below is exactly 16 bytes (one std140 vec4 row) so the C++
    // layout matches the GLSL uniform block exactly.
    // ════════════════════════════════════════════════════════════════════════

    // ── B1: Per-channel RGB tone curves ──────────────────────
    // 10 control points each, packed interleaved (x0,y0,x1,y1,...) identical
    // to `curvePoints` above so they map to GLSL `vec4 curveXxxPoints[5]`.
    struct CurvePoint curveRedPoints[10];
    struct CurvePoint curveGreenPoints[10];
    struct CurvePoint curveBluePoints[10];

    // ── B2: Color Calibration (red/green/blue hue+sat, shadows tint) ───────
    // vec4 calRedGreen   = (redHue, redSat, greenHue, greenSat)
    float calRedHue;
    float calRedSat;
    float calGreenHue;
    float calGreenSat;
    // vec4 calBlueShadow = (blueHue, blueSat, shadowsTint, pad)
    float calBlueHue;
    float calBlueSat;
    float calShadowsTint;
    float calPad;

    // ── B3: Dehaze (dark-channel prior) ──────────────────────
    float dehaze;
    float dehazePad1;
    float dehazePad2;
    float dehazePad3;

    // ── B4: Structure (fine local contrast) ──────────────────
    float structure;
    float structurePad1;
    float structurePad2;
    float structurePad3;

    // ── B5/B6: Luma & Color noise reduction ──────────────────
    float lumaNoiseReduction;
    float colorNoiseReduction;
    float nrPad1;
    float nrPad2;

    // ── B7: Chromatic aberration (red-cyan / blue-yellow) ────
    float chromaticAberrationRC;
    float chromaticAberrationBY;
    float caPad1;
    float caPad2;

    // ── B8: 3D LUT (texture bound at binding=3) ──────────────
    // lutInfo = (lutIntensity, hasLut, lutSize, pad)
    float lutIntensity;
    float hasLut;
    float lutSize;
    float lutPad;

    // ── B9/B10/B11: Glow / Halation / Flare ──────────────────
    float glowAmount;
    float halationAmount;
    float flareAmount;
    float ghfPad;

    // ── B12: Show clipping overlay ───────────────────────────
    int32_t showClipping;
    int32_t clipPad1;
    int32_t clipPad2;
    int32_t clipPad3;
};

// Result of probing a Vulkan device
struct VulkanDeviceInfo {
    bool supported;
    char deviceName[256];
    uint32_t apiVersion;
    uint32_t maxImageDimension;
};

class VulkanCompute {
public:
    VulkanCompute();
    ~VulkanCompute();

    // Check if Vulkan is available and populate device info
    VulkanDeviceInfo probeDevice();

    // Initialize the Vulkan compute pipeline for image processing
    // Returns true on success, false on failure
    bool initialize();

    // Process an RGBA image in-place using the current adjustments
    // pixels: RGBA8 packed data (4 bytes per pixel), row-major
    // width/height: image dimensions
    // Returns true on success
    bool processImage(uint8_t* pixels, int width, int height, const AdjustmentsUBO& adjustments);

    // Upload a 3D LUT (RGBA8, cube of side `size`, e.g. 33). Passing size<=0
    // reverts to the default identity LUT. The LUT is sampled at binding=3.
    bool setLut(const uint8_t* rgbaData, int size);

    // Release all Vulkan resources
    void release();

    bool isInitialized() const { return m_initialized; }

    // Current bound 3D LUT side length (e.g. 16 for the default identity LUT,
    // or the size passed to setLut). Used by the JNI layer to populate the UBO
    // `lutSize` field so the shader applies the correct half-texel offset.
    int lutSize() const { return m_lutSize; }

    // Check if the given image dimensions are safe to process on the GPU
    bool canProcessImage(int width, int height) const;

private:
    bool createInstance();
    bool pickPhysicalDevice();
    bool createLogicalDevice();
    bool createCommandPool();
    bool createComputePipeline();
    bool createDescriptorSetLayout();
    bool createPipelineLayout();
    bool createShaderModule(const uint32_t* code, size_t codeSize, VkShaderModule* outModule);
    bool createBuffer(VkDeviceSize size, VkBufferUsageFlags usage, VkMemoryPropertyFlags props,
                      VkBuffer& buffer, VkDeviceMemory& memory);
    uint32_t findMemoryType(uint32_t typeFilter, VkMemoryPropertyFlags props);
    void copyBuffer(VkBuffer src, VkBuffer dst, VkDeviceSize size);

    // LUT 3D texture helpers
    bool createLutTexture(int size, const uint8_t* rgbaData);
    void destroyLutTexture();
    void transitionLutLayout(VkImage image, VkImageLayout oldLayout, VkImageLayout newLayout);
    void writeLutDescriptor();

    VkInstance               m_instance        = VK_NULL_HANDLE;
    VkPhysicalDevice         m_physicalDevice   = VK_NULL_HANDLE;
    VkDevice                 m_device           = VK_NULL_HANDLE;
    VkQueue                  m_computeQueue     = VK_NULL_HANDLE;
    uint32_t                 m_computeQueueFamily = 0;
    VkCommandPool            m_commandPool      = VK_NULL_HANDLE;
    VkCommandBuffer          m_commandBuffer    = VK_NULL_HANDLE;
    VkDescriptorSetLayout    m_descriptorSetLayout = VK_NULL_HANDLE;
    VkPipelineLayout         m_pipelineLayout   = VK_NULL_HANDLE;
    VkPipeline               m_pipeline         = VK_NULL_HANDLE;
    VkDescriptorPool         m_descriptorPool   = VK_NULL_HANDLE;
    VkDescriptorSet          m_descriptorSet    = VK_NULL_HANDLE;

    // Per-frame buffers (recreated when image size changes)
    VkBuffer                 m_inputBuffer      = VK_NULL_HANDLE;
    VkDeviceMemory           m_inputMemory      = VK_NULL_HANDLE;
    VkBuffer                 m_outputBuffer     = VK_NULL_HANDLE;
    VkDeviceMemory           m_outputMemory     = VK_NULL_HANDLE;
    VkBuffer                 m_uniformBuffer    = VK_NULL_HANDLE;
    VkDeviceMemory           m_uniformMemory    = VK_NULL_HANDLE;
    VkBuffer                 m_uniformBuffer2   = VK_NULL_HANDLE;
    VkDeviceMemory           m_uniformMemory2   = VK_NULL_HANDLE;
    int                      m_uniformBufferIndex = 0;

    std::atomic<bool>        m_isProcessing{false};
    uint32_t                 m_maxImageDimension = 4096;
    bool                     m_initialized      = false;
    int                      m_lastWidth        = 0;
    int                      m_lastHeight       = 0;

    // LUT 3D texture (binding=3, combined image sampler)
    VkImage                  m_lutImage         = VK_NULL_HANDLE;
    VkDeviceMemory           m_lutMemory        = VK_NULL_HANDLE;
    VkImageView              m_lutView          = VK_NULL_HANDLE;
    VkSampler                m_lutSampler       = VK_NULL_HANDLE;
    int                      m_lutSize          = 0;
    bool                     m_lutBound         = false;
};
