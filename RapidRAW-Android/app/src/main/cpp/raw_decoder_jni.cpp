#include <jni.h>
#include <android/log.h>
#include <libraw/libraw.h>
#include <string>
#include <cmath>
#include <exception>

#define LOG_TAG "RawDecoderJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

static inline int clamp255(int v) {
    return v < 0 ? 0 : (v > 255 ? 255 : v);
}

static inline float srgbGamma(float v) {
    if (v <= 0.0031308f) {
        return v * 12.92f;
    } else {
        return 1.055f * std::pow(v, 1.0f / 2.4f) - 0.055f;
    }
}

/**
 * 安全设置 jintArray 的单个元素，带 null/越界校验。
 * 若发生 JNI 异常则返回 false。
 */
static bool safeSetIntArrayElement(JNIEnv *env, jintArray arr, jint value) {
    if (arr == nullptr) {
        LOGE("safeSetIntArrayElement: array is null");
        return false;
    }
    jsize len = env->GetArrayLength(arr);
    if (len < 1) {
        LOGE("safeSetIntArrayElement: array length %d < 1", len);
        return false;
    }
    env->SetIntArrayRegion(arr, 0, 1, &value);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("safeSetIntArrayElement: SetIntArrayRegion threw exception");
        return false;
    }
    return true;
}

JNIEXPORT jintArray JNICALL
Java_com_rapidraw_core_RawDecoder_decodeRaw(
    JNIEnv *env,
    jobject /*thiz*/,
    jstring path,
    jintArray outWidth,
    jintArray outHeight
) {
    try {
        if (path == nullptr) {
            LOGE("decodeRaw: path is null");
            return nullptr;
        }

        const char *cPath = env->GetStringUTFChars(path, nullptr);
        if (cPath == nullptr) {
            LOGE("decodeRaw: GetStringUTFChars returned null");
            return nullptr;
        }

        LibRaw raw;
        raw.imgdata.params.output_bps = 8;
        raw.imgdata.params.output_color = 1; // sRGB
        raw.imgdata.params.use_camera_wb = 1;
        raw.imgdata.params.use_auto_wb = 0;
        raw.imgdata.params.half_size = 0;
        raw.imgdata.params.user_qual = 3; // AHD demosaic
        raw.imgdata.params.gamm[0] = 1.0 / 2.4;
        raw.imgdata.params.gamm[1] = 12.92;

        int ret = raw.open_file(cPath);
        env->ReleaseStringUTFChars(path, cPath);

        if (ret != LIBRAW_SUCCESS) {
            LOGE("libraw open_file failed: %d", ret);
            return nullptr;
        }

        ret = raw.unpack();
        if (ret != LIBRAW_SUCCESS) {
            LOGE("libraw unpack failed: %d", ret);
            return nullptr;
        }

        ret = raw.dcraw_process();
        if (ret != LIBRAW_SUCCESS) {
            LOGE("libraw dcraw_process failed: %d", ret);
            return nullptr;
        }

        libraw_processed_image_t *img = raw.dcraw_make_mem_image(&ret);
        if (img == nullptr || ret != LIBRAW_SUCCESS) {
            LOGE("libraw dcraw_make_mem_image failed: %d", ret);
            if (img) raw.dcraw_clear_mem(img);
            return nullptr;
        }

        if (img->type != LIBRAW_IMAGE_BITMAP || img->colors != 3) {
            LOGE("Unexpected libraw image type: %d colors: %d", img->type, img->colors);
            raw.dcraw_clear_mem(img);
            return nullptr;
        }

        int width = static_cast<int>(img->width);
        int height = static_cast<int>(img->height);

        // 2026 正式版: 安全写回 width/height，防止 Java 层传入非法数组导致崩溃
        if (!safeSetIntArrayElement(env, outWidth, width) ||
            !safeSetIntArrayElement(env, outHeight, height)) {
            raw.dcraw_clear_mem(img);
            return nullptr;
        }

        //  Sanity check: 避免极端尺寸导致后续 OOM
        const int MAX_PIXELS = 200000000; // ~200MP
        int64_t pixelCount64 = static_cast<int64_t>(width) * static_cast<int64_t>(height);
        if (pixelCount64 > MAX_PIXELS || pixelCount64 <= 0) {
            LOGE("decodeRaw: image dimensions too large or invalid: %dx%d", width, height);
            raw.dcraw_clear_mem(img);
            return nullptr;
        }
        int pixelCount = static_cast<int>(pixelCount64);

        jintArray result = env->NewIntArray(pixelCount);
        if (result == nullptr) {
            LOGE("decodeRaw: NewIntArray failed (OOM?)");
            raw.dcraw_clear_mem(img);
            return nullptr;
        }

        jint *pixels = env->GetIntArrayElements(result, nullptr);
        if (pixels == nullptr) {
            LOGE("decodeRaw: GetIntArrayElements failed");
            raw.dcraw_clear_mem(img);
            return nullptr;
        }

        const unsigned char *data = img->data;
        int stride = width * 3;

        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                int srcIdx = y * stride + x * 3;
                int r = data[srcIdx];
                int g = data[srcIdx + 1];
                int b = data[srcIdx + 2];
                int dstIdx = y * width + x;
                pixels[dstIdx] = (0xFF << 24) | (r << 16) | (g << 8) | b;
            }
        }

        env->ReleaseIntArrayElements(result, pixels, 0);
        raw.dcraw_clear_mem(img);

        return result;
    } catch (const std::bad_alloc &e) {
        LOGE("decodeRaw: C++ OOM: %s", e.what());
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    } catch (const std::exception &e) {
        LOGE("decodeRaw: C++ exception: %s", e.what());
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    } catch (...) {
        LOGE("decodeRaw: unknown C++ exception");
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_rapidraw_core_RawDecoder_canDecodeRaw(
    JNIEnv *env,
    jobject /*thiz*/,
    jstring path
) {
    try {
        if (path == nullptr) return JNI_FALSE;

        const char *cPath = env->GetStringUTFChars(path, nullptr);
        if (cPath == nullptr) return JNI_FALSE;

        LibRaw raw;
        int ret = raw.open_file(cPath);
        env->ReleaseStringUTFChars(path, cPath);

        if (ret != LIBRAW_SUCCESS) return JNI_FALSE;

        // Try unpack (lightweight validation)
        ret = raw.unpack();
        return ret == LIBRAW_SUCCESS ? JNI_TRUE : JNI_FALSE;
    } catch (const std::bad_alloc &e) {
        LOGE("canDecodeRaw: C++ OOM: %s", e.what());
        if (env->ExceptionCheck()) env->ExceptionClear();
        return JNI_FALSE;
    } catch (const std::exception &e) {
        LOGE("canDecodeRaw: C++ exception: %s", e.what());
        if (env->ExceptionCheck()) env->ExceptionClear();
        return JNI_FALSE;
    } catch (...) {
        LOGE("canDecodeRaw: unknown C++ exception");
        if (env->ExceptionCheck()) env->ExceptionClear();
        return JNI_FALSE;
    }
}

} // extern "C"
