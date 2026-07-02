#pragma once

#include <vulkan/vulkan.h>
#include <cstdint>
#include <atomic>
#include <string>

// Maximum number of curve control points supported
static constexpr int MAX_CURVE_POINTS = 12;

// HSL channel adjustment
struct HslChannel {
    float hue;
    float saturation;
    float luminance;
};

// Color grading region (hue/sat/lum for shadows, midtones, highlights)
struct ColorGradingRegion {
    float hue;
    float saturation;
    float luminance;
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

    // ── Tone Curve (10 control points packed as x,y pairs) ──
    float curveX[10];
    float curveY[10];

    // ── Color Grading ────────────────────────────────────────
    ColorGradingRegion cgShadows;
    ColorGradingRegion cgMidtones;
    ColorGradingRegion cgHighlights;
    float cgBlend;        // 0.0 .. 1.0
    float cgBalance;      // -1.0 .. 1.0

    // ── CDL Lift/Gamma/Gain Per-Channel ──────────────────────
    float cdlShadowsR;
    float cdlShadowsG;
    float cdlShadowsB;
    float cdlMidtonesR;
    float cdlMidtonesG;
    float cdlMidtonesB;
    float cdlHighlightsR;
    float cdlHighlightsG;
    float cdlHighlightsB;
    float pad5[3];        // pad to 16-byte alignment

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
    int32_t toneMapMode;      // 0=AgX, 1=ACES, 2=Filmic, 3=Passthrough
    float agxContrast;        // 0.0 .. 1.0
    float agxPedestal;        // 0.0 .. 0.5
    float pad7;

    // ── Sharpening ───────────────────────────────────────────
    float sharpness;          // 0.0 .. 4.0
    float sharpenRadius;      // 0.5 .. 3.0
    float pad8[2];
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

    // Release all Vulkan resources
    void release();

    bool isInitialized() const { return m_initialized; }

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
};
