#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <cstring>
#include <cmath>

#include "vulkan_compute.h"

#define LOG_TAG "VulkanComputeJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global VulkanCompute instance (singleton per process)
static VulkanCompute* g_vulkanCompute = nullptr;

// ── Helper: Normalize adjustment values from the Kotlin data model ──────
// The Kotlin Adjustments data class uses different ranges than the GPU pipeline:
//   - contrast: -100..100 → -1..1
//   - highlights: -150..150 → -1..1
//   - shadows: -100..100 → -1..1
//   - whites: -30..30 → -1..1
//   - blacks: -60..60 → -1..1
//   - clarity: -100..100 → -1..1
//   - saturation: -100..100 → -1..1
//   - vibrance: -100..100 → -1..1
//   - temperature: -100..100 → mapped to Kelvin internally
//   - tint: -100..100 → -1..1
//   - HSL channels: -100..100 → -1..1
//   - etc.

static float norm(float value, float maxAbs) {
    return value / maxAbs;
}

static void fillAdjustmentsUBO(JNIEnv* env, jobject adjustments, AdjustmentsUBO& ubo, int width, int height) {
    std::memset(&ubo, 0, sizeof(ubo));

    ubo.width = (float)width;
    ubo.height = (float)height;

    // Get the Adjustments class
    jclass cls = env->GetObjectClass(adjustments);
    if (!cls) {
        LOGE("Failed to get Adjustments class");
        return;
    }

    // Helper macro to read a float field from the Adjustments object
    #define READ_FLOAT(name) \
        env->GetFloatField(adjustments, env->GetFieldID(cls, name, "F"))

    #define READ_INT(name) \
        env->GetIntField(adjustments, env->GetFieldID(cls, name, "I"))

    #define READ_BOOL(name) \
        env->GetBooleanField(adjustments, env->GetFieldID(cls, name, "Z"))

    // ── Basic ───────────────────────────────────────────────────
    ubo.exposure   = READ_FLOAT("exposure");
    ubo.brightness = norm(READ_FLOAT("brightness"), 5.0f);

    // ── White Balance ───────────────────────────────────────────
    // Temperature in Adjustments is -100..100, map to -1..1
    ubo.temperature = norm(READ_FLOAT("temperature"), 100.0f);
    ubo.tint        = norm(READ_FLOAT("tint"), 100.0f);

    // ── Tonal Controls ──────────────────────────────────────────
    ubo.contrast   = norm(READ_FLOAT("contrast"), 100.0f);
    ubo.highlights = norm(READ_FLOAT("highlights"), 150.0f);
    ubo.shadows    = norm(READ_FLOAT("shadows"), 100.0f);
    ubo.whites     = norm(READ_FLOAT("whites"), 30.0f);
    ubo.blacks     = norm(READ_FLOAT("blacks"), 60.0f);
    ubo.clarity    = norm(READ_FLOAT("clarity"), 100.0f);
    ubo.centre     = norm(READ_FLOAT("centre"), 100.0f);

    // ── Color ───────────────────────────────────────────────────
    ubo.saturation = norm(READ_FLOAT("saturation"), 100.0f);
    ubo.vibrance   = norm(READ_FLOAT("vibrance"), 100.0f);

    // ── HSL 8-Color Panel ───────────────────────────────────────
    // Each HSL channel is a nested HslChannel object with hue/saturation/luminance
    // All normalized from -100..100 → -1..1
    #define READ_HSL_CHANNEL(field, hslStruct) \
        do { \
            jobject hslObj = env->GetObjectField(adjustments, env->GetFieldID(cls, field, "Lcom/rapidraw/data/model/HslChannel;")); \
            if (hslObj) { \
                jclass hslCls = env->GetObjectClass(hslObj); \
                (hslStruct).hue        = norm(env->GetFloatField(hslObj, env->GetFieldID(hslCls, "hue", "F")), 100.0f); \
                (hslStruct).saturation = norm(env->GetFloatField(hslObj, env->GetFieldID(hslCls, "saturation", "F")), 100.0f); \
                (hslStruct).luminance  = norm(env->GetFloatField(hslObj, env->GetFieldID(hslCls, "luminance", "F")), 100.0f); \
                env->DeleteLocalRef(hslCls); \
                env->DeleteLocalRef(hslObj); \
            } \
        } while(0)

    READ_HSL_CHANNEL("hslReds",     ubo.hslRed);
    READ_HSL_CHANNEL("hslOranges",  ubo.hslOrange);
    READ_HSL_CHANNEL("hslYellows",  ubo.hslYellow);
    READ_HSL_CHANNEL("hslGreens",   ubo.hslGreen);
    READ_HSL_CHANNEL("hslAquas",    ubo.hslAqua);
    READ_HSL_CHANNEL("hslBlues",    ubo.hslBlue);
    READ_HSL_CHANNEL("hslPurples",  ubo.hslPurple);
    READ_HSL_CHANNEL("hslMagentas", ubo.hslMagenta);

    // ── Tone Curve ──────────────────────────────────────────────
    // The Adjustments model stores lumaCurve as List<Coord> (x,y pairs).
    // We need to pack up to 10 control points into the UBO.
    // The curve values in Coord are in 0..255 range, normalized to 0..1.
    {
        jobject curveList = env->GetObjectField(adjustments,
            env->GetFieldID(cls, "lumaCurve", "Ljava/util/List;"));
        if (curveList) {
            jclass listCls = env->GetObjectClass(curveList);
            jint size = env->CallIntMethod(curveList, env->GetMethodID(listCls, "size", "()I"));
            int numPoints = (size < 10) ? size : 10;
            jclass coordCls = env->FindClass("com/rapidraw/data/model/Coord");

            for (int i = 0; i < numPoints; i++) {
                jobject coord = env->CallObjectMethod(curveList,
                    env->GetMethodID(listCls, "get", "(I)Ljava/lang/Object;"), i);
                if (coord) {
                    float x = env->GetFloatField(coord, env->GetFieldID(coordCls, "x", "F"));
                    float y = env->GetFloatField(coord, env->GetFieldID(coordCls, "y", "F"));
                    ubo.curveX[i] = x / 255.0f;
                    ubo.curveY[i] = y / 255.0f;
                    env->DeleteLocalRef(coord);
                }
            }
            // Fill remaining points with identity (diagonal)
            for (int i = numPoints; i < 10; i++) {
                float t = (float)i / 9.0f;
                ubo.curveX[i] = t;
                ubo.curveY[i] = t;
            }
            env->DeleteLocalRef(coordCls);
            env->DeleteLocalRef(listCls);
            env->DeleteLocalRef(curveList);
        } else {
            // Default identity curve
            for (int i = 0; i < 10; i++) {
                float t = (float)i / 9.0f;
                ubo.curveX[i] = t;
                ubo.curveY[i] = t;
            }
        }
    }

    // ── Color Grading ───────────────────────────────────────────
    {
        jobject cg = env->GetObjectField(adjustments,
            env->GetFieldID(cls, "colorGrading", "Lcom/rapidraw/data/model/ColorGrading;"));
        if (cg) {
            jclass cgCls = env->GetObjectClass(cg);

            #define READ_CG_REGION(field, region) \
                do { \
                    jobject regObj = env->GetObjectField(cg, env->GetFieldID(cgCls, field, "Lcom/rapidraw/data/model/ColorGradingRegion;")); \
                    if (regObj) { \
                        jclass regCls = env->GetObjectClass(regObj); \
                        (region).hue        = env->GetFloatField(regObj, env->GetFieldID(regCls, "hue", "F")) / 360.0f; \
                        (region).saturation = env->GetFloatField(regObj, env->GetFieldID(regCls, "saturation", "F")) / 100.0f; \
                        (region).luminance  = norm(env->GetFloatField(regObj, env->GetFieldID(regCls, "luminance", "F")), 100.0f); \
                        env->DeleteLocalRef(regCls); \
                        env->DeleteLocalRef(regObj); \
                    } \
                } while(0)

            READ_CG_REGION("shadows",    ubo.cgShadows);
            READ_CG_REGION("midtones",   ubo.cgMidtones);
            READ_CG_REGION("highlights", ubo.cgHighlights);

            ubo.cgBlend   = env->GetFloatField(cg, env->GetFieldID(cgCls, "blending", "F")) / 100.0f;
            ubo.cgBalance = norm(env->GetFloatField(cg, env->GetFieldID(cgCls, "balance", "F")), 100.0f);

            env->DeleteLocalRef(cgCls);
            env->DeleteLocalRef(cg);
        }
    }

    // ── CDL Color Grading Per-Channel ───────────────────────────
    ubo.cdlShadowsR     = norm(READ_FLOAT("colorGradingShadowsR"),     100.0f);
    ubo.cdlShadowsG     = norm(READ_FLOAT("colorGradingShadowsG"),     100.0f);
    ubo.cdlShadowsB     = norm(READ_FLOAT("colorGradingShadowsB"),     100.0f);
    ubo.cdlMidtonesR    = norm(READ_FLOAT("colorGradingMidtonesR"),    100.0f);
    ubo.cdlMidtonesG    = norm(READ_FLOAT("colorGradingMidtonesG"),    100.0f);
    ubo.cdlMidtonesB    = norm(READ_FLOAT("colorGradingMidtonesB"),    100.0f);
    ubo.cdlHighlightsR  = norm(READ_FLOAT("colorGradingHighlightsR"),  100.0f);
    ubo.cdlHighlightsG  = norm(READ_FLOAT("colorGradingHighlightsG"),  100.0f);
    ubo.cdlHighlightsB  = norm(READ_FLOAT("colorGradingHighlightsB"),  100.0f);

    // ── Vignette ────────────────────────────────────────────────
    ubo.vignetteAmount    = norm(READ_FLOAT("vignetteAmount"), 100.0f);
    ubo.vignetteMidpoint  = READ_FLOAT("vignetteMidpoint") / 100.0f;
    ubo.vignetteRoundness = norm(READ_FLOAT("vignetteRoundness"), 100.0f);
    ubo.vignetteFeather   = READ_FLOAT("vignetteFeather") / 100.0f;

    // ── Film Grain ──────────────────────────────────────────────
    ubo.grainAmount    = READ_FLOAT("grainAmount") / 100.0f;
    ubo.grainSize      = READ_FLOAT("grainSize") / 25.0f;  // 25 is midpoint → size ~1.0
    if (ubo.grainSize < 0.5f) ubo.grainSize = 0.5f;
    ubo.grainRoughness = READ_FLOAT("grainRoughness") / 100.0f;

    // ── Tone Mapping ────────────────────────────────────────────
    // colorScienceMode: 0=AgX, 1=ACES, 2=OpenDRT, 3=Standard(passthrough)
    ubo.toneMapMode  = READ_INT("colorScienceMode");
    if (ubo.toneMapMode > 3) ubo.toneMapMode = 3;
    ubo.agxContrast  = READ_FLOAT("agxContrast");
    ubo.agxPedestal  = READ_FLOAT("agxPedestal");

    // ── Sharpening ──────────────────────────────────────────────
    ubo.sharpness     = READ_FLOAT("sharpness") / 100.0f * 4.0f;  // 0..150 → 0..4.0 (capped)
    if (ubo.sharpness > 4.0f) ubo.sharpness = 4.0f;
    ubo.sharpenRadius = 1.0f;

    #undef READ_FLOAT
    #undef READ_INT
    #undef READ_BOOL
    #undef READ_HSL_CHANNEL
    #undef READ_CG_REGION
}

// ═══════════════════════════════════════════════════════════════════════════
// JNI Functions
// ═══════════════════════════════════════════════════════════════════════════

extern "C" {

/**
 * Check if Vulkan compute is supported on this device.
 * Returns true if a Vulkan instance can be created and a compute-capable device exists.
 */
JNIEXPORT jboolean JNICALL
Java_com_rapidraw_core_VulkanBackend_nativeIsVulkanSupported(JNIEnv* /*env*/, jobject /*thiz*/) {
    VulkanCompute vc;
    VulkanDeviceInfo info = vc.probeDevice();
    return info.supported ? JNI_TRUE : JNI_FALSE;
}

/**
 * Probe the Vulkan device and return device info as a String array:
 * [0] = device name
 * [1] = API version string (e.g. "1.1.0")
 * [2] = max image dimension
 * Returns null if Vulkan is not available.
 */
JNIEXPORT jobjectArray JNICALL
Java_com_rapidraw_core_VulkanBackend_nativeProbeVulkanDevice(JNIEnv* env, jobject /*thiz*/) {
    VulkanCompute vc;
    VulkanDeviceInfo info = vc.probeDevice();

    if (!info.supported) {
        return nullptr;
    }

    // Decode API version
    uint32_t major = VK_VERSION_MAJOR(info.apiVersion);
    uint32_t minor = VK_VERSION_MINOR(info.apiVersion);
    uint32_t patch = VK_VERSION_PATCH(info.apiVersion);

    char apiStr[32];
    snprintf(apiStr, sizeof(apiStr), "%u.%u.%u", major, minor, patch);

    char maxDimStr[16];
    snprintf(maxDimStr, sizeof(maxDimStr), "%u", info.maxImageDimension);

    // Create String[] result
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(3, stringClass, env->NewStringUTF(""));
    env->SetObjectArrayElement(result, 0, env->NewStringUTF(info.deviceName));
    env->SetObjectArrayElement(result, 1, env->NewStringUTF(apiStr));
    env->SetObjectArrayElement(result, 2, env->NewStringUTF(maxDimStr));

    return result;
}

/**
 * Initialize the Vulkan compute backend.
 * Must be called before processImage.
 * Returns true on success.
 */
JNIEXPORT jboolean JNICALL
Java_com_rapidraw_core_VulkanBackend_nativeInitialize(JNIEnv* /*env*/, jobject /*thiz*/) {
    if (g_vulkanCompute) {
        delete g_vulkanCompute;
        g_vulkanCompute = nullptr;
    }

    g_vulkanCompute = new VulkanCompute();
    if (!g_vulkanCompute->initialize()) {
        LOGE("Failed to initialize VulkanCompute");
        delete g_vulkanCompute;
        g_vulkanCompute = nullptr;
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/**
 * Process a Bitmap using Vulkan compute.
 * The bitmap is modified in place.
 * Returns the processed bitmap on success, or null on failure.
 */
JNIEXPORT jobject JNICALL
Java_com_rapidraw_core_VulkanBackend_nativeProcessImage(
    JNIEnv* env,
    jobject /*thiz*/,
    jobject bitmap,
    jobject adjustments
) {
    if (!g_vulkanCompute || !g_vulkanCompute->isInitialized()) {
        LOGE("VulkanCompute not initialized");
        return nullptr;
    }

    // Get bitmap info
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed to get bitmap info");
        return nullptr;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format must be RGBA_8888, got %d", info.format);
        return nullptr;
    }

    int width = (int)info.width;
    int height = (int)info.height;

    // Lock bitmap pixels
    uint8_t* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, (void**)&pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed to lock bitmap pixels");
        return nullptr;
    }

    // Fill UBO from Kotlin Adjustments object
    AdjustmentsUBO ubo;
    fillAdjustmentsUBO(env, adjustments, ubo, width, height);

    // Process image
    bool success = g_vulkanCompute->processImage(pixels, width, height, ubo);

    // Unlock bitmap pixels
    AndroidBitmap_unlockPixels(env, bitmap);

    if (!success) {
        LOGE("VulkanCompute::processImage failed");
        return nullptr;
    }

    return bitmap;
}

/**
 * Release all Vulkan resources.
 */
JNIEXPORT void JNICALL
Java_com_rapidraw_core_VulkanBackend_nativeRelease(JNIEnv* /*env*/, jobject /*thiz*/) {
    if (g_vulkanCompute) {
        g_vulkanCompute->release();
        delete g_vulkanCompute;
        g_vulkanCompute = nullptr;
    }
}

} // extern "C"
