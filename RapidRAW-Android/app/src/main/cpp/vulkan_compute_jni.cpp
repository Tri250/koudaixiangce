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

// Read a List<Coord> curve field (x,y in 0..255) into a CurvePoint[10] array,
// normalizing to 0..1 and filling unused slots with the identity diagonal.
// Used for the per-channel R/G/B tone curves (B1). Mirrors the inline lumaCurve
// parsing logic above but is reusable.
static void readCoordCurveList(JNIEnv* env, jobject adjustments, jclass cls,
                               const char* fieldName,
                               AdjustmentsUBO::CurvePoint* outPoints, int maxPoints) {
    // Identity default.
    for (int i = 0; i < maxPoints; i++) {
        float t = (float)i / (float)(maxPoints - 1);
        outPoints[i].x = t;
        outPoints[i].y = t;
    }

    jfieldID fid = env->GetFieldID(cls, fieldName, "Ljava/util/List;");
    if (!fid || env->ExceptionCheck()) {
        env->ExceptionClear();
        return;
    }
    jobject list = env->GetObjectField(adjustments, fid);
    if (!list) return;

    jclass listCls = env->GetObjectClass(list);
    jmethodID sizeMethod = env->GetMethodID(listCls, "size", "()I");
    jmethodID getMethod = env->GetMethodID(listCls, "get", "(I)Ljava/lang/Object;");
    if (!sizeMethod || !getMethod || env->ExceptionCheck()) {
        env->ExceptionClear();
        env->DeleteLocalRef(listCls);
        env->DeleteLocalRef(list);
        return;
    }

    jint size = env->CallIntMethod(list, sizeMethod);
    int n = (size < maxPoints) ? size : maxPoints;

    jclass coordCls = env->FindClass("com/rapidraw/data/model/Coord");
    if (!coordCls || env->ExceptionCheck()) {
        env->ExceptionClear();
        env->DeleteLocalRef(listCls);
        env->DeleteLocalRef(list);
        return;
    }
    jfieldID xFid = env->GetFieldID(coordCls, "x", "F");
    jfieldID yFid = env->GetFieldID(coordCls, "y", "F");
    if (!xFid || !yFid || env->ExceptionCheck()) {
        env->ExceptionClear();
        env->DeleteLocalRef(coordCls);
        env->DeleteLocalRef(listCls);
        env->DeleteLocalRef(list);
        return;
    }

    for (int i = 0; i < n; i++) {
        jobject coord = env->CallObjectMethod(list, getMethod, i);
        if (coord) {
            outPoints[i].x = env->GetFloatField(coord, xFid) / 255.0f;
            outPoints[i].y = env->GetFloatField(coord, yFid) / 255.0f;
            env->DeleteLocalRef(coord);
        }
    }

    env->DeleteLocalRef(coordCls);
    env->DeleteLocalRef(listCls);
    env->DeleteLocalRef(list);
}

static void fillAdjustmentsUBO(JNIEnv* env, jobject adjustments, AdjustmentsUBO& ubo,
                               int width, int height, int lutSize) {
    std::memset(&ubo, 0, sizeof(ubo));

    ubo.width = (float)width;
    ubo.height = (float)height;

    // Get the Adjustments class
    jclass cls = env->GetObjectClass(adjustments);
    if (env->ExceptionCheck()) {
        LOGE("JNI exception after GetObjectClass");
        env->ExceptionClear();
        return;
    }
    if (!cls) {
        LOGE("Failed to get Adjustments class");
        return;
    }

    // 2026 正式版: GetFieldID 在 R8 极端情况下可能返回 null，必须校验。
    #define JNI_CHECK_EXCEPTION(label) \
        do { \
            if (env->ExceptionCheck()) { \
                LOGE("JNI exception at %s", label); \
                env->ExceptionClear(); \
            } \
        } while(0)

    #define SAFE_GET_FIELD_ID(name, sig) \
        env->GetFieldID(cls, name, sig)

    #define READ_FLOAT(name) \
        ({ jfieldID fid_ = SAFE_GET_FIELD_ID(name, "F"); \
           fid_ ? env->GetFloatField(adjustments, fid_) : 0.0f; })

    #define READ_INT(name) \
        ({ jfieldID fid_ = SAFE_GET_FIELD_ID(name, "I"); \
           fid_ ? env->GetIntField(adjustments, fid_) : 0; })

    #define READ_BOOL(name) \
        ({ jfieldID fid_ = SAFE_GET_FIELD_ID(name, "Z"); \
           fid_ ? env->GetBooleanField(adjustments, fid_) : JNI_FALSE; })

    JNI_CHECK_EXCEPTION("SAFE_GET_FIELD_ID / READ_ macros");

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
            jfieldID hslFieldId = env->GetFieldID(cls, field, "Lcom/rapidraw/data/model/HslChannel;"); \
            if (hslFieldId == nullptr || env->ExceptionCheck()) { \
                env->ExceptionClear(); \
                LOGE("Failed to get field ID for HSL channel %s", field); \
                break; \
            } \
            jobject hslObj = env->GetObjectField(adjustments, hslFieldId); \
            if (hslObj) { \
                jclass hslCls = env->GetObjectClass(hslObj); \
                jfieldID hueFid = env->GetFieldID(hslCls, "hue", "F"); \
                jfieldID satFid = env->GetFieldID(hslCls, "saturation", "F"); \
                jfieldID lumFid = env->GetFieldID(hslCls, "luminance", "F"); \
                if (hueFid && satFid && lumFid) { \
                    (hslStruct).hue        = norm(env->GetFloatField(hslObj, hueFid), 100.0f); \
                    (hslStruct).saturation = norm(env->GetFloatField(hslObj, satFid), 100.0f); \
                    (hslStruct).luminance  = norm(env->GetFloatField(hslObj, lumFid), 100.0f); \
                } \
                env->DeleteLocalRef(hslCls); \
                env->DeleteLocalRef(hslObj); \
            } \
            JNI_CHECK_EXCEPTION(field); \
        } while(0)

    READ_HSL_CHANNEL("hslReds",     ubo.hslRed);
    READ_HSL_CHANNEL("hslOranges",  ubo.hslOrange);
    READ_HSL_CHANNEL("hslYellows",  ubo.hslYellow);
    READ_HSL_CHANNEL("hslGreens",   ubo.hslGreen);
    READ_HSL_CHANNEL("hslAquas",    ubo.hslAqua);
    READ_HSL_CHANNEL("hslBlues",    ubo.hslBlue);
    READ_HSL_CHANNEL("hslPurples",  ubo.hslPurple);
    READ_HSL_CHANNEL("hslMagentas", ubo.hslMagenta);

    JNI_CHECK_EXCEPTION("HSL channels block");

    // ── Tone Curve ──────────────────────────────────────────────
    // The Adjustments model stores lumaCurve as List<Coord> (x,y pairs).
    // We need to pack up to 10 control points into the UBO.
    // The curve values in Coord are in 0..255 range, normalized to 0..1.
    {
        jfieldID lumaCurveFid = env->GetFieldID(cls, "lumaCurve", "Ljava/util/List;");
        if (lumaCurveFid == nullptr || env->ExceptionCheck()) {
            env->ExceptionClear();
            LOGE("Failed to get field ID for lumaCurve");
            // Fill with identity curve (interleaved x,y layout)
            for (int i = 0; i < 10; i++) {
                float t = (float)i / 9.0f;
                ubo.curvePoints[i].x = t;
                ubo.curvePoints[i].y = t;
            }
            // Don't return, just skip the curve parsing
            goto tone_curve_done;
        }
        jobject curveList = env->GetObjectField(adjustments, lumaCurveFid);
        if (curveList) {
            jclass listCls = env->GetObjectClass(curveList);
            if (listCls == nullptr || env->ExceptionCheck()) {
                env->ExceptionClear();
                env->DeleteLocalRef(curveList);
                goto tone_curve_identity;
            }
            jmethodID sizeMethod = env->GetMethodID(listCls, "size", "()I");
            if (sizeMethod == nullptr || env->ExceptionCheck()) {
                env->ExceptionClear();
                env->DeleteLocalRef(listCls);
                env->DeleteLocalRef(curveList);
                goto tone_curve_identity;
            }
            jmethodID getMethod = env->GetMethodID(listCls, "get", "(I)Ljava/lang/Object;");
            if (getMethod == nullptr || env->ExceptionCheck()) {
                env->ExceptionClear();
                env->DeleteLocalRef(listCls);
                env->DeleteLocalRef(curveList);
                goto tone_curve_identity;
            }
            jint size = env->CallIntMethod(curveList, sizeMethod);
            int numPoints = (size < 10) ? size : 10;
            jclass coordCls = env->FindClass("com/rapidraw/data/model/Coord");
            if (coordCls == nullptr || env->ExceptionCheck()) {
                env->ExceptionClear();
                env->DeleteLocalRef(listCls);
                env->DeleteLocalRef(curveList);
                goto tone_curve_identity;
            }
            jfieldID coordXFid = env->GetFieldID(coordCls, "x", "F");
            jfieldID coordYFid = env->GetFieldID(coordCls, "y", "F");
            if (coordXFid == nullptr || coordYFid == nullptr || env->ExceptionCheck()) {
                env->ExceptionClear();
                env->DeleteLocalRef(coordCls);
                env->DeleteLocalRef(listCls);
                env->DeleteLocalRef(curveList);
                goto tone_curve_identity;
            }

            for (int i = 0; i < numPoints; i++) {
                jobject coord = env->CallObjectMethod(curveList, getMethod, i);
                if (coord) {
                    float x = env->GetFloatField(coord, coordXFid);
                    float y = env->GetFloatField(coord, coordYFid);
                    ubo.curvePoints[i].x = x / 255.0f;
                    ubo.curvePoints[i].y = y / 255.0f;
                    env->DeleteLocalRef(coord);
                }
            }
            // Fill remaining points with identity (diagonal)
            for (int i = numPoints; i < 10; i++) {
                float t = (float)i / 9.0f;
                ubo.curvePoints[i].x = t;
                ubo.curvePoints[i].y = t;
            }
            env->DeleteLocalRef(coordCls);
            env->DeleteLocalRef(listCls);
            env->DeleteLocalRef(curveList);
        } else {
            tone_curve_identity:
            // Default identity curve (interleaved x,y layout)
            for (int i = 0; i < 10; i++) {
                float t = (float)i / 9.0f;
                ubo.curvePoints[i].x = t;
                ubo.curvePoints[i].y = t;
            }
        }
        tone_curve_done:
        JNI_CHECK_EXCEPTION("tone curve block");
    }

    // ── Color Grading ───────────────────────────────────────────
    {
        jobject cg = env->GetObjectField(adjustments,
            env->GetFieldID(cls, "colorGrading", "Lcom/rapidraw/data/model/ColorGrading;"));
        if (cg) {
            jclass cgCls = env->GetObjectClass(cg);

            #define READ_CG_REGION(field, region) \
                do { \
                    jfieldID cgFieldId = env->GetFieldID(cgCls, field, "Lcom/rapidraw/data/model/ColorGradingRegion;"); \
                    if (cgFieldId == nullptr || env->ExceptionCheck()) { \
                        env->ExceptionClear(); \
                        LOGE("Failed to get field ID for CG region %s", field); \
                        break; \
                    } \
                    jobject regObj = env->GetObjectField(cg, cgFieldId); \
                    if (regObj) { \
                        jclass regCls = env->GetObjectClass(regObj); \
                        jfieldID hueFid = env->GetFieldID(regCls, "hue", "F"); \
                        jfieldID satFid = env->GetFieldID(regCls, "saturation", "F"); \
                        jfieldID lumFid = env->GetFieldID(regCls, "luminance", "F"); \
                        if (hueFid && satFid && lumFid) { \
                            (region).hue        = env->GetFloatField(regObj, hueFid) / 360.0f; \
                            (region).saturation = env->GetFloatField(regObj, satFid) / 100.0f; \
                            (region).luminance  = norm(env->GetFloatField(regObj, lumFid), 100.0f); \
                        } \
                        env->DeleteLocalRef(regCls); \
                        env->DeleteLocalRef(regObj); \
                    } \
                    JNI_CHECK_EXCEPTION(field); \
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

    JNI_CHECK_EXCEPTION("color grading block");

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

    // ── B1: Per-channel R/G/B tone curves ──────────────────────
    // Same List<Coord> layout as lumaCurve (x,y in 0..255 → 0..1).
    readCoordCurveList(env, adjustments, cls, "redCurve",   ubo.curveRedPoints,   10);
    readCoordCurveList(env, adjustments, cls, "greenCurve", ubo.curveGreenPoints, 10);
    readCoordCurveList(env, adjustments, cls, "blueCurve",  ubo.curveBluePoints,  10);
    JNI_CHECK_EXCEPTION("RGB curve block");

    // ── B2: Color Calibration ──────────────────────────────────
    // Nested ColorCalibration object. All fields are -100..100 in Kotlin and
    // are normalized to -1..1 (matching ImageProcessor / Float32Pipeline /
    // GpuPipeline conventions).
    {
        jfieldID ccFid = env->GetFieldID(cls, "colorCalibration",
                                         "Lcom/rapidraw/data/model/ColorCalibration;");
        if (ccFid && !env->ExceptionCheck()) {
            jobject cc = env->GetObjectField(adjustments, ccFid);
            if (cc) {
                jclass ccCls = env->GetObjectClass(cc);
                #define READ_CC_FLOAT(name) \
                    ({ jfieldID f_ = env->GetFieldID(ccCls, name, "F"); \
                       f_ ? env->GetFloatField(cc, f_) : 0.0f; })
                ubo.calRedHue      = norm(READ_CC_FLOAT("redHue"),         100.0f);
                ubo.calRedSat      = norm(READ_CC_FLOAT("redSaturation"), 100.0f);
                ubo.calGreenHue    = norm(READ_CC_FLOAT("greenHue"),       100.0f);
                ubo.calGreenSat    = norm(READ_CC_FLOAT("greenSaturation"),100.0f);
                ubo.calBlueHue     = norm(READ_CC_FLOAT("blueHue"),        100.0f);
                ubo.calBlueSat     = norm(READ_CC_FLOAT("blueSaturation"), 100.0f);
                ubo.calShadowsTint = norm(READ_CC_FLOAT("shadowsTint"),    100.0f);
                #undef READ_CC_FLOAT
                env->DeleteLocalRef(ccCls);
                env->DeleteLocalRef(cc);
            }
        } else {
            env->ExceptionClear();
        }
        JNI_CHECK_EXCEPTION("color calibration block");
    }

    // ── B3/B4: Dehaze & Structure ──────────────────────────────
    // Both -100..100 in Kotlin → -1..1. Shader clamps to [0,1] internally.
    ubo.dehaze    = norm(READ_FLOAT("dehaze"),    100.0f);
    ubo.structure = norm(READ_FLOAT("structure"), 100.0f);

    // ── B5/B6: Noise Reduction ─────────────────────────────────
    // 0..100 in Kotlin → 0..1.
    ubo.lumaNoiseReduction  = norm(READ_FLOAT("lumaNoiseReduction"),  100.0f);
    ubo.colorNoiseReduction = norm(READ_FLOAT("colorNoiseReduction"), 100.0f);

    // ── B7: Chromatic Aberration ───────────────────────────────
    // -100..100 in Kotlin → -1..1.
    ubo.chromaticAberrationRC = norm(READ_FLOAT("chromaticAberrationRedCyan"),   100.0f);
    ubo.chromaticAberrationBY = norm(READ_FLOAT("chromaticAberrationBlueYellow"),100.0f);

    // ── B8: 3D LUT ─────────────────────────────────────────────
    // lutIntensity: 0..100 → 0..1. hasLut is set when a LUT is active
    // (activeLutId non-empty) AND intensity > 0. lutSize reflects the actually
    // bound 3D texture side (queried from VulkanCompute) for correct sampling.
    ubo.lutIntensity = norm(READ_FLOAT("lutIntensity"), 100.0f);
    {
        bool hasLut = false;
        jfieldID lutIdFid = env->GetFieldID(cls, "activeLutId", "Ljava/lang/String;");
        if (lutIdFid && !env->ExceptionCheck()) {
            jobject lutIdObj = env->GetObjectField(adjustments, lutIdFid);
            if (lutIdObj) {
                jstring lutIdStr = static_cast<jstring>(lutIdObj);
                jsize len = env->GetStringLength(lutIdStr);
                if (len > 0) hasLut = true;
                env->DeleteLocalRef(lutIdObj);
            }
        } else {
            env->ExceptionClear();
        }
        ubo.hasLut = (hasLut && ubo.lutIntensity > 0.0f) ? 1.0f : 0.0f;
        ubo.lutSize = (lutSize > 0) ? (float)lutSize : 16.0f;
    }
    JNI_CHECK_EXCEPTION("LUT block");

    // ── B9/B10/B11: Glow / Halation / Flare ────────────────────
    // 0..100 in Kotlin → 0..1.
    ubo.glowAmount     = norm(READ_FLOAT("glowAmount"),     100.0f);
    ubo.halationAmount = norm(READ_FLOAT("halationAmount"), 100.0f);
    ubo.flareAmount    = norm(READ_FLOAT("flareAmount"),    100.0f);

    // ── B12: Show clipping ─────────────────────────────────────
    ubo.showClipping = READ_BOOL("showClipping") ? 1 : 0;

    #undef READ_FLOAT
    #undef READ_INT
    #undef READ_BOOL
    #undef READ_HSL_CHANNEL
    #undef READ_CG_REGION

    JNI_CHECK_EXCEPTION("end of fillAdjustmentsUBO");
    #undef JNI_CHECK_EXCEPTION
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
    fillAdjustmentsUBO(env, adjustments, ubo, width, height, g_vulkanCompute->lutSize());

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
 * Upload a 3D LUT to the Vulkan compute backend.
 * `data` is an RGBA8 byte array of length size*size*size*4 (x=R fastest, then
 * G, then B). Pass size<=0 (or a null array) to revert to the default identity
 * LUT. Returns true on success.
 */
JNIEXPORT jboolean JNICALL
Java_com_rapidraw_core_VulkanBackend_nativeSetLut(JNIEnv* env, jobject /*thiz*/,
                                                   jbyteArray data, jint size) {
    if (!g_vulkanCompute || !g_vulkanCompute->isInitialized()) {
        LOGE("nativeSetLut: VulkanCompute not initialized");
        return JNI_FALSE;
    }

    if (data == nullptr || size <= 0) {
        return g_vulkanCompute->setLut(nullptr, 0) ? JNI_TRUE : JNI_FALSE;
    }

    jsize expected = size * size * size * 4;
    jsize len = env->GetArrayLength(data);
    if (len < expected) {
        LOGE("nativeSetLut: data too short (got %d, need %d for size=%d)", len, expected, size);
        return JNI_FALSE;
    }

    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    if (!bytes) {
        LOGE("nativeSetLut: GetByteArrayElements failed");
        return JNI_FALSE;
    }

    bool ok = g_vulkanCompute->setLut(reinterpret_cast<const uint8_t*>(bytes), size);

    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
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
