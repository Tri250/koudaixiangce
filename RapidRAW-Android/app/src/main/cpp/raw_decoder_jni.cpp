#include <jni.h>
#include <android/log.h>
#include <libraw/libraw.h>
#include <string>
#include <cmath>

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

JNIEXPORT jintArray JNICALL
Java_com_rapidraw_core_RawDecoder_decodeRaw(
    JNIEnv *env,
    jobject /*thiz*/,
    jstring path,
    jintArray outWidth,
    jintArray outHeight
) {
    const char *cPath = env->GetStringUTFChars(path, nullptr);
    if (cPath == nullptr) {
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

    // Write back width/height
    env->SetIntArrayRegion(outWidth, 0, 1, &width);
    env->SetIntArrayRegion(outHeight, 0, 1, &height);

    int pixelCount = width * height;
    jintArray result = env->NewIntArray(pixelCount);
    if (result == nullptr) {
        raw.dcraw_clear_mem(img);
        return nullptr;
    }

    jint *pixels = env->GetIntArrayElements(result, nullptr);
    if (pixels == nullptr) {
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
}

JNIEXPORT jboolean JNICALL
Java_com_rapidraw_core_RawDecoder_canDecodeRaw(
    JNIEnv *env,
    jobject /*thiz*/,
    jstring path
) {
    const char *cPath = env->GetStringUTFChars(path, nullptr);
    if (cPath == nullptr) return JNI_FALSE;

    LibRaw raw;
    int ret = raw.open_file(cPath);
    env->ReleaseStringUTFChars(path, cPath);

    if (ret != LIBRAW_SUCCESS) return JNI_FALSE;

    // Try unpack (lightweight validation)
    ret = raw.unpack();
    return ret == LIBRAW_SUCCESS ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
