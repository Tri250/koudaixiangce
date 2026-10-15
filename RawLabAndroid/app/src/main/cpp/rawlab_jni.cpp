#include <jni.h>
#include "render_request.h"
#include <cstring>
#include <limits>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <vector>
#include <string>
#include <algorithm>
#include <cmath>

static_assert(sizeof(sony2fuji_photo_effects_config) == 40,
    "Photo effects config ABI must remain a versioned 40-byte struct");

namespace {
struct SessionDeleter {
    void operator()(sony2fuji_session* session) const { sony2fuji_session_destroy(session); }
};
using Session = std::unique_ptr<sony2fuji_session, SessionDeleter>;
// Opaque IDs keep stale Java handles from becoming native pointers.
std::mutex sessionsMutex;
std::unordered_map<jlong, Session> sessions;
jlong nextId = 1;

void throwJava(JNIEnv* env, const char* type, const char* message) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass(type), message);
}

struct NativeStatusError : std::runtime_error {
    sony2fuji_status status;
    NativeStatusError(sony2fuji_status value)
        : std::runtime_error(sony2fuji_status_message(value)), status(value) {}
};

void throwNativeStatus(JNIEnv* env, const NativeStatusError& error) {
    if (env->ExceptionCheck()) return;
    auto type = env->FindClass("com/rawlab/android/NativeProcessingException");
    if (!type) {
        env->ExceptionClear();
        throwJava(env, "java/io/IOException", error.what());
        return;
    }
    auto constructor = env->GetMethodID(type, "<init>", "(ILjava/lang/String;)V");
    if (!constructor) {
        env->ExceptionClear();
        throwJava(env, "java/io/IOException", error.what());
        return;
    }
    auto message = env->NewStringUTF(error.what());
    if (!message) return;
    auto exception = env->NewObject(type, constructor, static_cast<jint>(error.status), message);
    if (exception) env->Throw(static_cast<jthrowable>(exception));
}

void check(sony2fuji_status status) {
    if (status == SONY2FUJI_STATUS_IO_ERROR || status == SONY2FUJI_STATUS_OUTPUT_IO_ERROR)
        throw NativeStatusError(status);
    rawlab::checkStatus(status);
}
struct UtfChars {
    JNIEnv* env;
    jstring value;
    const char* chars;
    UtfChars(JNIEnv* e, jstring s) : env(e), value(s), chars(s ? e->GetStringUTFChars(s, nullptr) : nullptr) {
        if (s && !chars) throw std::bad_alloc();
    }
    ~UtfChars() { if (chars) env->ReleaseStringUTFChars(value, chars); }
};
struct Buffer {
    sony2fuji_buffer value{};
    ~Buffer() { sony2fuji_release_buffer(&value); }
};
void prepareRecipeWb(sony2fuji_session* session, const char* input, bool active, float strength,
    bool& custom, float& temperature, float& tint) {
    if (!active || (!custom && tint * strength == 0)) return;
    // 临时显影只解析目标 RAW 的拍摄白平衡，随后正式请求重设完整配置。
    sony2fuji_hsl_config hsl{SONY2FUJI_HSL_CONFIG_VERSION, sizeof(sony2fuji_hsl_config)};
    sony2fuji_wavelet_denoise_config denoise{SONY2FUJI_WAVELET_DENOISE_CONFIG_VERSION, sizeof(sony2fuji_wavelet_denoise_config)};
    sony2fuji_photo_effects_config effects{SONY2FUJI_PHOTO_EFFECTS_CONFIG_VERSION, sizeof(sony2fuji_photo_effects_config)};
    check(sony2fuji_session_set_hsl(session, &hsl));
    check(sony2fuji_session_set_wavelet_denoise(session, &denoise));
    check(sony2fuji_session_set_photo_effects(session, &effects));
    check(sony2fuji_session_set_chroma_denoise(session, 0));
    check(sony2fuji_session_set_interactive_preview(session, 0));
    auto neutral = rawlab::makeRequest(input, nullptr, nullptr, 0, 0, false, 6500, 0, 32, rawlab::ExportFormat::JPEG);
    Buffer buffer;
    check(sony2fuji_process(session, &neutral, &buffer.value));
    float cameraTemperature = 0, cameraTint = 0;
    check(sony2fuji_session_get_raw_white_balance(session, &cameraTemperature, &cameraTint));
    if (!std::isfinite(cameraTemperature) || !std::isfinite(cameraTint))
        throw std::runtime_error("Cannot resolve target RAW white balance for master recipe");
    if (!custom) temperature = std::round(cameraTemperature);
    tint = std::clamp(std::round(cameraTint) + tint * strength, -150.0f, 150.0f);
    custom = true;
}
}

namespace {
sony2fuji_session* sessionFor(jlong id) {
    const auto found = sessions.find(id);
    if (found == sessions.end()) throw std::invalid_argument("Processor is closed");
    return found->second.get();
}
jobjectArray strings(JNIEnv* env, const std::vector<std::string>& values) {
    auto type = env->FindClass("java/lang/String");
    if (!type) return nullptr;
    auto constructor = env->GetMethodID(type, "<init>", "([BLjava/lang/String;)V");
    auto charset = env->NewStringUTF("UTF-8");
    if (!constructor || !charset) return nullptr;
    auto result = env->NewObjectArray(static_cast<jsize>(values.size()), type, nullptr);
    if (!result) return nullptr;
    for (size_t i = 0; i < values.size() && !env->ExceptionCheck(); ++i) {
        auto bytes = env->NewByteArray(static_cast<jsize>(values[i].size()));
        if (!bytes) return nullptr;
        env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(values[i].size()), reinterpret_cast<const jbyte*>(values[i].data()));
        auto value = env->NewObject(type, constructor, bytes, charset);
        if (!value) return nullptr;
        env->SetObjectArrayElement(result, static_cast<jsize>(i), value);
        env->DeleteLocalRef(value);
        env->DeleteLocalRef(bytes);
    }
    return env->ExceptionCheck() ? nullptr : result;
}
}

extern "C" JNIEXPORT void JNICALL
Java_com_rawlab_android_NativeProcessor_nativeSetLensDatabase(JNIEnv* env, jobject, jlong id, jstring path) {
    try {
        std::lock_guard<std::mutex> lock(sessionsMutex);
        UtfChars directory(env, path);
        check(sony2fuji_session_set_lens_database(sessionFor(id), directory.chars));
    } catch (const std::exception& error) { throwJava(env, "java/io/IOException", error.what()); }
}

extern "C" JNIEXPORT void JNICALL
Java_com_rawlab_android_NativeProcessor_nativeSetLensCorrection(JNIEnv* env, jobject, jlong id,
    jboolean distortion, jboolean tca, jboolean crop, jbyteArray profile) {
    try {
        std::lock_guard<std::mutex> lock(sessionsMutex);
        if (!profile) throw std::invalid_argument("Missing lens profile ID");
        std::string value(static_cast<size_t>(env->GetArrayLength(profile)), '\0');
        env->GetByteArrayRegion(profile, 0, static_cast<jsize>(value.size()), reinterpret_cast<jbyte*>(value.data()));
        if (env->ExceptionCheck()) return;
        sony2fuji_lens_correction_config config{SONY2FUJI_LENS_CORRECTION_CONFIG_VERSION,
            sizeof(config), distortion ? 1 : 0, tca ? 1 : 0, crop ? 1 : 0, value.c_str()};
        check(sony2fuji_session_set_lens_correction(sessionFor(id), &config));
    } catch (const std::exception& error) { throwJava(env, "java/lang/IllegalArgumentException", error.what()); }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_rawlab_android_NativeProcessor_nativeLensProfiles(JNIEnv* env, jobject, jlong id, jstring input) {
    try {
        std::lock_guard<std::mutex> lock(sessionsMutex);
        auto* session = sessionFor(id);
        UtfChars path(env, input);
        sony2fuji_lens_report report{};
        uint32_t count = 0;
        check(sony2fuji_session_inspect_lens(session, path.chars, &report, nullptr, 0, &count));
        std::vector<sony2fuji_lens_profile> profiles(count);
        check(sony2fuji_session_inspect_lens(session, path.chars, &report, profiles.data(), count, &count));
        std::vector<std::string> values;
        values.reserve(static_cast<size_t>(count) * 6);
        for (const auto& profile : profiles) {
            values.insert(values.end(), {profile.id, profile.maker, profile.model,
                profile.has_distortion ? "1" : "0", profile.has_tca ? "1" : "0", profile.is_match ? "1" : "0"});
        }
        return strings(env, values);
    } catch (const std::exception& error) { throwJava(env, "java/io/IOException", error.what()); }
    return nullptr;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_rawlab_android_NativeProcessor_nativeLensReport(JNIEnv* env, jobject, jlong id) {
    try {
        std::lock_guard<std::mutex> lock(sessionsMutex);
        sony2fuji_lens_report report{};
        check(sony2fuji_session_get_lens_report(sessionFor(id), &report));
        return strings(env, {std::to_string(report.state), report.lens_model, report.profile.model,
            report.applied_distortion ? "1" : "0", report.applied_tca ? "1" : "0", std::to_string(report.crop_scale)});
    } catch (const std::exception& error) { throwJava(env, "java/io/IOException", error.what()); }
    return nullptr;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_rawlab_android_NativeLookBridge_validate(JNIEnv* env, jclass, jstring path) {
    try {
        UtfChars value(env, path);
        sony2fuji_look_format format = SONY2FUJI_LOOK_UNKNOWN;
        uint32_t version = 0;
        const auto status = sony2fuji_validate_look(value.chars, &format, &version);
        if (status != SONY2FUJI_STATUS_OK) {
            const char* type = status == SONY2FUJI_STATUS_INVALID_ARGUMENT
                ? "java/lang/IllegalArgumentException"
                : status == SONY2FUJI_STATUS_UNSUPPORTED
                    ? "java/lang/UnsupportedOperationException"
                    : status == SONY2FUJI_STATUS_OUT_OF_MEMORY
                        ? "java/lang/OutOfMemoryError"
                        : "java/io/IOException";
            throwJava(env, type, sony2fuji_status_message(status));
            return nullptr;
        }
        auto result = env->NewIntArray(2);
        if (!result) return nullptr;
        const jint values[] = {static_cast<jint>(format), static_cast<jint>(version)};
        env->SetIntArrayRegion(result, 0, 2, values);
        return env->ExceptionCheck() ? nullptr : result;
    } catch (const std::bad_alloc&) {
        throwJava(env, "java/lang/OutOfMemoryError", "Not enough memory to validate look");
    } catch (const std::exception& error) {
        throwJava(env, "java/io/IOException", error.what());
    }
    return nullptr;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_rawlab_android_NativeProcessor_nativeCreate(JNIEnv* env, jobject, jint mode) {
    try {
        std::lock_guard<std::mutex> lock(sessionsMutex);
        if (mode < SONY2FUJI_GPU_OFF || mode > SONY2FUJI_GPU_FORCE) throw std::invalid_argument("Invalid GPU mode");
        sony2fuji_session* pointer = nullptr;
        check(sony2fuji_session_create(&pointer));
        Session session(pointer);
        sony2fuji_gpu_config config{SONY2FUJI_GPU_CONFIG_VERSION, sizeof(sony2fuji_gpu_config), static_cast<sony2fuji_gpu_mode>(mode)};
        check(sony2fuji_session_set_gpu_config(pointer, &config));
        const jlong id = nextId++;
        sessions.emplace(id, std::move(session));
        return id;
    } catch (const std::bad_alloc&) {
        throwJava(env, "java/lang/OutOfMemoryError", "Not enough memory to create a RAW session");
        return 0;
    } catch (const std::exception& error) {
        throwJava(env, "java/lang/IllegalStateException", error.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_rawlab_android_NativeProcessor_nativeDestroy(JNIEnv*, jobject, jlong id) {
    std::lock_guard<std::mutex> lock(sessionsMutex);
    sessions.erase(id);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rawlab_android_NativeProcessor_nativeSetGpuMode(JNIEnv* env, jobject, jlong id, jint mode) {
    try {
        std::lock_guard<std::mutex> lock(sessionsMutex);
        const auto found = sessions.find(id);
        if (found == sessions.end() || mode < 0 || mode > 2) throw std::invalid_argument("Invalid processor or GPU mode");
        sony2fuji_gpu_config config{SONY2FUJI_GPU_CONFIG_VERSION, sizeof(config), static_cast<sony2fuji_gpu_mode>(mode)};
        check(sony2fuji_session_set_gpu_config(found->second.get(), &config));
    } catch (const std::exception& error) { throwJava(env, "java/lang/IllegalArgumentException", error.what()); }
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_rawlab_android_NativeProcessor_nativeProcess(JNIEnv* env, jobject, jlong id,
    jstring input, jstring lut, jstring output, jfloat strength, jfloat exposure,
    jboolean customWb, jfloat temperature, jfloat tint, jfloat highlights, jfloat shadows,
    jfloat contrast, jfloat toneCurve, jfloat saturation, jfloat sharpening,
    jint edge, jboolean interactive, jint format, jint longEdge,
    jboolean denoiseEnabled, jfloat luma, jfloat chroma, jfloat coarse, jint displayChromaDenoise,
    jfloat vignetteAmount, jfloat vignetteMidpoint, jfloat vignetteRoundness, jfloat vignetteFeather,
    jfloat vignetteHighlights, jfloat grainAmount, jfloat grainSize, jfloat grainRoughness,
    jint exposureMode, jfloatArray hslValues, jboolean recipeActive, jfloatArray curvePoints,
    jboolean cropActive, jfloatArray cropRect) {
    try {
        std::lock_guard<std::mutex> lock(sessionsMutex);
        const auto found = sessions.find(id);
        if (found == sessions.end()) throw std::invalid_argument("Processor is closed");
        auto* session = found->second.get();
        check(sony2fuji_session_set_raw_exposure_mode(session, static_cast<sony2fuji_raw_exposure_mode>(exposureMode)));
        UtfChars in(env, input), film(env, lut), out(env, output);
        bool effectiveCustom = customWb;
        prepareRecipeWb(session, in.chars, recipeActive, strength, effectiveCustom, temperature, tint);
        if (!hslValues || env->GetArrayLength(hslValues) != 24) throw std::invalid_argument("Invalid HSL values");
        float hslData[24];
        env->GetFloatArrayRegion(hslValues, 0, 24, hslData);
        if (env->ExceptionCheck()) return nullptr;
        sony2fuji_hsl_config hsl{};
        hsl.version = SONY2FUJI_HSL_CONFIG_VERSION;
        hsl.struct_size = sizeof(hsl);
        std::copy(hslData, hslData+8, hsl.hue);
        std::copy(hslData+8, hslData+16, hsl.saturation);
        std::copy(hslData+16, hslData+24, hsl.lightness);
        check(sony2fuji_session_set_hsl(session, &hsl));
        // v0.7.0 multi-point tone curve: flattened (x, y) anchors restated on
        // every render; empty clears. Applied after the neutral recipe metering
        // so camera white balance sampling stays curve-free.
        if (curvePoints) {
            const jsize pairs = env->GetArrayLength(curvePoints);
            if (pairs < 0 || pairs % 2 != 0 || pairs > 2 * SONY2FUJI_TONE_CURVE_MAX_POINTS)
                throw std::invalid_argument("Invalid tone curve anchors");
            std::vector<float> curveData(static_cast<size_t>(pairs));
            if (pairs > 0) env->GetFloatArrayRegion(curvePoints, 0, pairs, curveData.data());
            if (env->ExceptionCheck()) return nullptr;
            std::vector<sony2fuji_curve_point> anchors(static_cast<size_t>(pairs) / 2);
            for (size_t i = 0; i < anchors.size(); ++i) { anchors[i].x = curveData[2 * i]; anchors[i].y = curveData[2 * i + 1]; }
            check(sony2fuji_session_set_tone_curve(session, anchors.empty() ? nullptr : anchors.data(),
                static_cast<uint32_t>(anchors.size())));
        }
        // v0.7.0 crop/level: restated on every render like the tone curve. The
        // preview path clears it because the on-screen crop frame is composited
        // by the UI; only final renders carry an active normalized rectangle.
        if (cropActive) {
            if (!cropRect || env->GetArrayLength(cropRect) != 5)
                throw std::invalid_argument("Invalid crop rectangle");
            float cropData[5];
            env->GetFloatArrayRegion(cropRect, 0, 5, cropData);
            if (env->ExceptionCheck()) return nullptr;
            sony2fuji_crop_config crop{SONY2FUJI_CROP_CONFIG_VERSION, sizeof(sony2fuji_crop_config),
                cropData[0], cropData[1], cropData[2], cropData[3], cropData[4]};
            check(sony2fuji_session_set_crop(session, &crop));
        } else {
            check(sony2fuji_session_set_crop(session, nullptr));
        }
        const auto outputFormat = rawlab::parseExportFormat(format);
        auto request = rawlab::makeRequest(in.chars, film.chars, out.chars, strength,
            exposure, effectiveCustom, temperature, tint, highlights, shadows, contrast,
            toneCurve, saturation, sharpening, edge, outputFormat,
            longEdge < 0 ? 0u : static_cast<uint32_t>(longEdge));
        if (recipeActive) {
            request.exposure_ev *= strength;
            request.highlights *= strength;
            request.shadows *= strength;
            request.contrast = 1 + contrast * strength;
            request.saturation = 1 + saturation * strength;
        }
        check(sony2fuji_session_set_interactive_preview(session, interactive && !out.chars));
        check(sony2fuji_session_set_viewport_preview(session, !out.chars && edge > 0));
        sony2fuji_wavelet_denoise_config denoise{SONY2FUJI_WAVELET_DENOISE_CONFIG_VERSION,
            sizeof(sony2fuji_wavelet_denoise_config), denoiseEnabled ? 1 : 0, luma, chroma, coarse};
        check(sony2fuji_session_set_wavelet_denoise(session, &denoise));
        sony2fuji_photo_effects_config effects{SONY2FUJI_PHOTO_EFFECTS_CONFIG_VERSION,
            sizeof(sony2fuji_photo_effects_config), vignetteAmount, vignetteMidpoint, vignetteRoundness,
            vignetteFeather, vignetteHighlights, grainAmount, grainSize, grainRoughness};
        check(sony2fuji_session_set_photo_effects(session, &effects));
        check(sony2fuji_session_set_chroma_denoise(session, displayChromaDenoise));
        Buffer buffer;
        check(sony2fuji_process(session, &request, &buffer.value));
        if (output) return nullptr;
        const auto& b = buffer.value;
        const size_t row = static_cast<size_t>(b.width) * 4;
        const size_t bytes = rawlab::previewByteCount(b);
        if (bytes > static_cast<size_t>(std::numeric_limits<jsize>::max()))
            throw std::runtime_error("Invalid preview buffer");
        auto pixels = env->NewByteArray(static_cast<jsize>(bytes));
        if (!pixels) return nullptr;
        for (uint32_t y = 0; y < b.height && !env->ExceptionCheck(); ++y) {
            env->SetByteArrayRegion(pixels, static_cast<jsize>(y * row), static_cast<jsize>(row),
                reinterpret_cast<const jbyte*>(b.data) + y * b.stride_bytes);
        }
        if (env->ExceptionCheck()) return nullptr;
        float kelvin = NAN, wbTint = NAN;
        sony2fuji_session_get_raw_white_balance(session, &kelvin, &wbTint);
        kelvin = std::round(kelvin); wbTint = std::round(wbTint);
        auto type = env->FindClass("com/rawlab/android/NativeFrame");
        if (!type) return nullptr;
        auto constructor = env->GetMethodID(type, "<init>", "(II[BFFI)V");
        if (!constructor) return nullptr;
        return env->NewObject(type, constructor, static_cast<jint>(b.width),
            static_cast<jint>(b.height), pixels, kelvin, wbTint, static_cast<jint>(sony2fuji_session_get_last_backend(session)));
    } catch (const NativeStatusError& error) {
        throwNativeStatus(env, error);
    } catch (const std::bad_alloc&) {
        throwJava(env, "java/lang/OutOfMemoryError", "Not enough memory to develop this RAW");
    } catch (const std::invalid_argument& error) {
        throwJava(env, "java/lang/IllegalArgumentException", error.what());
    } catch (const std::exception& error) {
        throwJava(env, "java/io/IOException", error.what());
    } catch (...) {
        throwJava(env, "java/io/IOException", "RAW processing failed");
    }
    return nullptr;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_rawlab_android_NativeProcessor_nativeAutoExposure(JNIEnv* env, jobject, jlong id,
    jstring input, jboolean customWb, jfloat temperature, jfloat tint, jint exposureMode, jboolean recipeActive, jfloat strength) {
    try {
        std::lock_guard<std::mutex> lock(sessionsMutex);
        const auto found = sessions.find(id);
        if (found == sessions.end()) throw std::invalid_argument("Processor is closed");
        UtfChars in(env, input);
        check(sony2fuji_session_set_raw_exposure_mode(found->second.get(), static_cast<sony2fuji_raw_exposure_mode>(exposureMode)));
        bool effectiveCustom = customWb;
        prepareRecipeWb(found->second.get(), in.chars, recipeActive, strength, effectiveCustom, temperature, tint);
        auto request = rawlab::makeRequest(in.chars, nullptr, nullptr, 0, 0, effectiveCustom,
            temperature, tint, 0, 0, 0, 0, 0, 0, 1, rawlab::ExportFormat::JPEG);
        check(sony2fuji_session_set_raw_exposure_mode(found->second.get(), static_cast<sony2fuji_raw_exposure_mode>(exposureMode)));
        float ev = 0;
        const auto status = sony2fuji_session_estimate_auto_exposure(found->second.get(), &request, &ev);
        if (status == SONY2FUJI_STATUS_OUT_OF_MEMORY) throw std::bad_alloc();
        check(status);
        return ev;
    } catch (const NativeStatusError& error) { throwNativeStatus(env, error); }
    catch (const std::bad_alloc&) { throwJava(env, "java/lang/OutOfMemoryError", "Not enough memory to meter this RAW"); }
    catch (const std::exception& error) { throwJava(env, "java/io/IOException", error.what()); }
    catch (...) { throwJava(env, "java/io/IOException", "Automatic exposure failed"); }
    return 0;
}

// ---- 普通照片（JPEG/PNG/HEIC）：平台解码后的 RGBA8 像素走核心照片管线 ----

// 照片没有 RAW 拍摄白平衡可解析，配方只按强度缩放影调参数，白平衡保持用户当前值。
static void scaleRecipeForPhoto(sony2fuji_request& request, jboolean recipeActive, jfloat strength,
    jfloat contrast, jfloat saturation) {
    if (!recipeActive) return;
    request.exposure_ev *= strength;
    request.highlights *= strength;
    request.shadows *= strength;
    request.contrast = 1 + contrast * strength;
    request.saturation = 1 + saturation * strength;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_rawlab_android_NativeProcessor_nativeProcessPhoto(JNIEnv* env, jobject, jlong id,
    jbyteArray pixels, jint width, jint height, jstring lut, jstring output, jfloat strength,
    jfloat exposure, jboolean customWb, jfloat temperature, jfloat tint, jfloat highlights,
    jfloat shadows, jfloat contrast, jfloat toneCurve, jfloat saturation, jfloat sharpening,
    jint edge, jboolean interactive, jboolean png, jint longEdge,
    jboolean denoiseEnabled, jfloat luma, jfloat chroma, jfloat coarse, jint displayChromaDenoise,
    jfloat vignetteAmount, jfloat vignetteMidpoint, jfloat vignetteRoundness, jfloat vignetteFeather,
    jfloat vignetteHighlights, jfloat grainAmount, jfloat grainSize, jfloat grainRoughness,
    jint exposureMode, jfloatArray hslValues, jboolean recipeActive, jfloatArray curvePoints,
    jboolean cropActive, jfloatArray cropRect) {
    try {
        std::lock_guard<std::mutex> lock(sessionsMutex);
        const auto found = sessions.find(id);
        if (found == sessions.end()) throw std::invalid_argument("Processor is closed");
        auto* session = found->second.get();
        check(sony2fuji_session_set_raw_exposure_mode(session, static_cast<sony2fuji_raw_exposure_mode>(exposureMode)));
        UtfChars film(env, lut), out(env, output);
        if (!pixels || width <= 0 || height <= 0 ||
            env->GetArrayLength(pixels) != static_cast<jsize>(width) * height * 4)
            throw std::invalid_argument("Invalid photo pixels");
        if (!hslValues || env->GetArrayLength(hslValues) != 24) throw std::invalid_argument("Invalid HSL values");
        float hslData[24];
        env->GetFloatArrayRegion(hslValues, 0, 24, hslData);
        if (env->ExceptionCheck()) return nullptr;
        sony2fuji_hsl_config hsl{};
        hsl.version = SONY2FUJI_HSL_CONFIG_VERSION;
        hsl.struct_size = sizeof(hsl);
        std::copy(hslData, hslData+8, hsl.hue);
        std::copy(hslData+8, hslData+16, hsl.saturation);
        std::copy(hslData+16, hslData+24, hsl.lightness);
        check(sony2fuji_session_set_hsl(session, &hsl));
        // v0.7.0 multi-point tone curve: same contract as the file path.
        if (curvePoints) {
            const jsize pairs = env->GetArrayLength(curvePoints);
            if (pairs < 0 || pairs % 2 != 0 || pairs > 2 * SONY2FUJI_TONE_CURVE_MAX_POINTS)
                throw std::invalid_argument("Invalid tone curve anchors");
            std::vector<float> curveData(static_cast<size_t>(pairs));
            if (pairs > 0) env->GetFloatArrayRegion(curvePoints, 0, pairs, curveData.data());
            if (env->ExceptionCheck()) return nullptr;
            std::vector<sony2fuji_curve_point> anchors(static_cast<size_t>(pairs) / 2);
            for (size_t i = 0; i < anchors.size(); ++i) { anchors[i].x = curveData[2 * i]; anchors[i].y = curveData[2 * i + 1]; }
            check(sony2fuji_session_set_tone_curve(session, anchors.empty() ? nullptr : anchors.data(),
                static_cast<uint32_t>(anchors.size())));
        }
        // v0.7.0 crop/level: previews clear it (the UI composites the frame);
        // photo exports carry the active normalized rectangle.
        if (cropActive) {
            if (!cropRect || env->GetArrayLength(cropRect) != 5)
                throw std::invalid_argument("Invalid crop rectangle");
            float cropData[5];
            env->GetFloatArrayRegion(cropRect, 0, 5, cropData);
            if (env->ExceptionCheck()) return nullptr;
            sony2fuji_crop_config crop{SONY2FUJI_CROP_CONFIG_VERSION, sizeof(sony2fuji_crop_config),
                cropData[0], cropData[1], cropData[2], cropData[3], cropData[4]};
            check(sony2fuji_session_set_crop(session, &crop));
        } else {
            check(sony2fuji_session_set_crop(session, nullptr));
        }
        const jsize byteCount = static_cast<jsize>(width) * height * 4;
        std::vector<uint8_t> rgba(static_cast<size_t>(byteCount));
        env->GetByteArrayRegion(pixels, 0, byteCount, reinterpret_cast<jbyte*>(rgba.data()));
        if (env->ExceptionCheck()) return nullptr;
        auto request = rawlab::makeBufferRequest(rgba.data(), static_cast<uint32_t>(width),
            static_cast<uint32_t>(height), film.chars, out.chars, strength, exposure, customWb,
            temperature, tint, highlights, shadows, contrast, toneCurve, saturation, sharpening,
            edge, png, longEdge < 0 ? 0u : static_cast<uint32_t>(longEdge));
        scaleRecipeForPhoto(request, recipeActive, strength, contrast, saturation);
        check(sony2fuji_session_set_interactive_preview(session, interactive && !out.chars));
        sony2fuji_wavelet_denoise_config denoise{SONY2FUJI_WAVELET_DENOISE_CONFIG_VERSION,
            sizeof(sony2fuji_wavelet_denoise_config), denoiseEnabled ? 1 : 0, luma, chroma, coarse};
        check(sony2fuji_session_set_wavelet_denoise(session, &denoise));
        sony2fuji_photo_effects_config effects{SONY2FUJI_PHOTO_EFFECTS_CONFIG_VERSION,
            sizeof(sony2fuji_photo_effects_config), vignetteAmount, vignetteMidpoint, vignetteRoundness,
            vignetteFeather, vignetteHighlights, grainAmount, grainSize, grainRoughness};
        check(sony2fuji_session_set_photo_effects(session, &effects));
        check(sony2fuji_session_set_chroma_denoise(session, displayChromaDenoise));
        Buffer buffer;
        check(sony2fuji_process(session, &request, &buffer.value));
        if (output) return nullptr;
        const auto& b = buffer.value;
        const size_t row = static_cast<size_t>(b.width) * 4;
        const size_t bytes = rawlab::previewByteCount(b);
        if (bytes > static_cast<size_t>(std::numeric_limits<jsize>::max()))
            throw std::runtime_error("Invalid preview buffer");
        auto outPixels = env->NewByteArray(static_cast<jsize>(bytes));
        if (!outPixels) return nullptr;
        for (uint32_t y = 0; y < b.height && !env->ExceptionCheck(); ++y) {
            env->SetByteArrayRegion(outPixels, static_cast<jsize>(y * row), static_cast<jsize>(row),
                reinterpret_cast<const jbyte*>(b.data) + y * b.stride_bytes);
        }
        if (env->ExceptionCheck()) return nullptr;
        // 照片没有拍摄白平衡：回传请求中的等效值，UI 按当前设置显示。
        const float kelvin = customWb ? temperature : 6500.0f;
        const float wbTint = customWb ? tint : 0.0f;
        auto type = env->FindClass("com/rawlab/android/NativeFrame");
        if (!type) return nullptr;
        auto constructor = env->GetMethodID(type, "<init>", "(II[BFFI)V");
        if (!constructor) return nullptr;
        return env->NewObject(type, constructor, static_cast<jint>(b.width),
            static_cast<jint>(b.height), outPixels, kelvin, wbTint,
            static_cast<jint>(sony2fuji_session_get_last_backend(session)));
    } catch (const std::bad_alloc&) {
        throwJava(env, "java/lang/OutOfMemoryError", "Not enough memory to process this photo");
    } catch (const std::invalid_argument& error) {
        throwJava(env, "java/lang/IllegalArgumentException", error.what());
    } catch (const std::exception& error) {
        throwJava(env, "java/io/IOException", error.what());
    } catch (...) {
        throwJava(env, "java/io/IOException", "Photo processing failed");
    }
    return nullptr;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_rawlab_android_NativeProcessor_nativeAutoExposurePhoto(JNIEnv* env, jobject, jlong id,
    jbyteArray pixels, jint width, jint height, jboolean customWb, jfloat temperature, jfloat tint,
    jint exposureMode, jboolean recipeActive, jfloat strength) {
    try {
        std::lock_guard<std::mutex> lock(sessionsMutex);
        const auto found = sessions.find(id);
        if (found == sessions.end()) throw std::invalid_argument("Processor is closed");
        if (!pixels || width <= 0 || height <= 0 ||
            env->GetArrayLength(pixels) != static_cast<jsize>(width) * height * 4)
            throw std::invalid_argument("Invalid photo pixels");
        check(sony2fuji_session_set_raw_exposure_mode(found->second.get(), static_cast<sony2fuji_raw_exposure_mode>(exposureMode)));
        const jsize byteCount = static_cast<jsize>(width) * height * 4;
        std::vector<uint8_t> rgba(static_cast<size_t>(byteCount));
        env->GetByteArrayRegion(pixels, 0, byteCount, reinterpret_cast<jbyte*>(rgba.data()));
        if (env->ExceptionCheck()) return 0;
        auto request = rawlab::makeBufferRequest(rgba.data(), static_cast<uint32_t>(width),
            static_cast<uint32_t>(height), nullptr, nullptr, 0, 0, customWb, temperature, tint,
            0, 0, 0, 0, 0, 0, 1, false);
        scaleRecipeForPhoto(request, recipeActive, strength, 0, 0);
        float ev = 0;
        const auto status = sony2fuji_session_estimate_auto_exposure(found->second.get(), &request, &ev);
        if (status == SONY2FUJI_STATUS_OUT_OF_MEMORY) throw std::bad_alloc();
        check(status);
        return ev;
    } catch (const std::bad_alloc&) { throwJava(env, "java/lang/OutOfMemoryError", "Not enough memory to meter this photo"); }
    catch (const std::exception& error) { throwJava(env, "java/io/IOException", error.what()); }
    catch (...) { throwJava(env, "java/io/IOException", "Automatic exposure failed"); }
    return 0;
}
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_rawlab_android_NativeProcessor_nativePickWhiteBalance(JNIEnv* env, jobject, jlong id,
    jfloat nx, jfloat ny) {
    try {
        std::lock_guard<std::mutex> lock(sessionsMutex);
        auto* session = sessionFor(id);
        float temperature = 0, tint = 0;
        // UNSUPPORTED (no RAW calibration) maps to IOException like other
        // non-OK statuses, so the UI can surface a "cannot sample" notice.
        check(sony2fuji_session_pick_white_balance(session, nx, ny, &temperature, &tint));
        if (!std::isfinite(temperature) || !std::isfinite(tint))
            throw std::runtime_error("White balance sample unavailable");
        auto result = env->NewFloatArray(2);
        if (!result) return nullptr;
        const jfloat values[] = {temperature, tint};
        env->SetFloatArrayRegion(result, 0, 2, values);
        return env->ExceptionCheck() ? nullptr : result;
    } catch (const NativeStatusError& error) { throwNativeStatus(env, error); }
    catch (const std::bad_alloc&) { throwJava(env, "java/lang/OutOfMemoryError", "Not enough memory to sample white balance"); }
    catch (const std::invalid_argument& error) { throwJava(env, "java/lang/IllegalArgumentException", error.what()); }
    catch (const std::exception& error) { throwJava(env, "java/io/IOException", error.what()); }
    catch (...) { throwJava(env, "java/io/IOException", "White balance sampling failed"); }
    return nullptr;
}

