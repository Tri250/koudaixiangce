#include <jni.h>
#include <android/log.h>
#include <libraw/libraw.h>
#include <string>
#include <cmath>
#include <exception>
#include <sys/stat.h>
#include <cstdio>

#define LOG_TAG "RawDecoderJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Error codes for getDecodeError():
// 0 = SUCCESS
// 1 = FILE_DAMAGED
// 2 = UNSUPPORTED_FORMAT
// 3 = DECODE_FAILED
// 4 = OOM
// 5 = TOO_LARGE
static thread_local int g_lastDecodeError = 0;

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

/**
 * 文件头校验：检查 RAW 文件的完整性（大小、魔数）。
 * 返回 true 表示文件看起来合法；false 表示文件损坏或格式不支持。
 * 失败时通过 *outError 设置具体错误码（FILE_DAMAGED / UNSUPPORTED_FORMAT）。
 */
static bool validateRawFile(const char *path, int *outError) {
    if (path == nullptr) {
        *outError = 1; // FILE_DAMAGED
        return false;
    }

    FILE *fp = fopen(path, "rb");
    if (fp == nullptr) {
        LOGE("validateRawFile: cannot open file: %s", path);
        *outError = 1; // FILE_DAMAGED
        return false;
    }

    // 获取文件大小
    struct stat st;
    if (fstat(fileno(fp), &st) != 0) {
        LOGE("validateRawFile: fstat failed");
        fclose(fp);
        *outError = 1; // FILE_DAMAGED
        return false;
    }

    int64_t fileSize = static_cast<int64_t>(st.st_size);
    if (fileSize < 4096) {
        // 文件太小，不太可能是合法的 RAW 文件（最小 RAW 文件通常 > 4KB）
        LOGE("validateRawFile: file too small (%lld bytes)", (long long)fileSize);
        fclose(fp);
        *outError = 1; // FILE_DAMAGED
        return false;
    }

    // 读取前 16 字节，检查常见 RAW 魔数
    unsigned char header[16] = {0};
    size_t readBytes = fread(header, 1, sizeof(header), fp);
    fclose(fp);

    if (readBytes < sizeof(header)) {
        LOGE("validateRawFile: cannot read header (got %zu bytes)", readBytes);
        *outError = 1; // FILE_DAMAGED
        return false;
    }

    // TIFF 格式魔数: "II" (little-endian, 0x49 0x49) 或 "MM" (big-endian, 0x4D 0x4D)
    // 紧接着是 TIFF 魔数 0x002A（II 后为 0x2A 0x00，MM 后为 0x00 0x2A）
    bool valid = false;
    if (header[0] == 0x49 && header[1] == 0x49) {
        // Little-endian TIFF: check bytes 2-3 for 0x002A (stored as 0x2A, 0x00)
        if (header[2] == 0x2A && header[3] == 0x00) {
            valid = true;
        }
    } else if (header[0] == 0x4D && header[1] == 0x4D) {
        // Big-endian TIFF: check bytes 2-3 for 0x002A (stored as 0x00, 0x2A)
        if (header[2] == 0x00 && header[3] == 0x2A) {
            valid = true;
        }
    }

    if (!valid) {
        LOGE("validateRawFile: invalid RAW header magic bytes");
        *outError = 1; // FILE_DAMAGED
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
            g_lastDecodeError = 1; // FILE_DAMAGED
            return nullptr;
        }

        const char *cPath = env->GetStringUTFChars(path, nullptr);
        if (cPath == nullptr) {
            LOGE("decodeRaw: GetStringUTFChars returned null");
            g_lastDecodeError = 1; // FILE_DAMAGED
            return nullptr;
        }

        // 文件头校验：在调用 LibRaw 之前检查文件完整性
        {
            int fileError = 0;
            if (!validateRawFile(cPath, &fileError)) {
                LOGE("decodeRaw: file validation failed, error=%d", fileError);
                g_lastDecodeError = fileError;
                env->ReleaseStringUTFChars(path, cPath);
                return nullptr;
            }
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
            g_lastDecodeError = 2; // UNSUPPORTED_FORMAT
            return nullptr;
        }

        ret = raw.unpack();
        if (ret != LIBRAW_SUCCESS) {
            LOGE("libraw unpack failed: %d", ret);
            g_lastDecodeError = 3; // DECODE_FAILED
            return nullptr;
        }

        ret = raw.dcraw_process();
        if (ret != LIBRAW_SUCCESS) {
            LOGE("libraw dcraw_process failed: %d", ret);
            g_lastDecodeError = 3; // DECODE_FAILED
            return nullptr;
        }

        libraw_processed_image_t *img = raw.dcraw_make_mem_image(&ret);
        if (img == nullptr || ret != LIBRAW_SUCCESS) {
            LOGE("libraw dcraw_make_mem_image failed: %d", ret);
            g_lastDecodeError = 3; // DECODE_FAILED
            if (img) raw.dcraw_clear_mem(img);
            return nullptr;
        }

        if (img->type != LIBRAW_IMAGE_BITMAP || img->colors != 3) {
            LOGE("Unexpected libraw image type: %d colors: %d", img->type, img->colors);
            g_lastDecodeError = 3; // DECODE_FAILED
            raw.dcraw_clear_mem(img);
            return nullptr;
        }

        int width = static_cast<int>(img->width);
        int height = static_cast<int>(img->height);

        // 2026 正式版: 安全写回 width/height，防止 Java 层传入非法数组导致崩溃
        if (!safeSetIntArrayElement(env, outWidth, width) ||
            !safeSetIntArrayElement(env, outHeight, height)) {
            g_lastDecodeError = 3; // DECODE_FAILED
            raw.dcraw_clear_mem(img);
            return nullptr;
        }

        //  Sanity check: 避免极端尺寸导致后续 OOM
        const int MAX_PIXELS = 200000000; // ~200MP
        int64_t pixelCount64 = static_cast<int64_t>(width) * static_cast<int64_t>(height);
        if (pixelCount64 > MAX_PIXELS || pixelCount64 <= 0) {
            LOGE("decodeRaw: image dimensions too large or invalid: %dx%d", width, height);
            g_lastDecodeError = 5; // TOO_LARGE
            raw.dcraw_clear_mem(img);
            return nullptr;
        }
        int pixelCount = static_cast<int>(pixelCount64);

        jintArray result = env->NewIntArray(pixelCount);
        if (result == nullptr) {
            LOGE("decodeRaw: NewIntArray failed (OOM?)");
            g_lastDecodeError = 4; // OOM
            raw.dcraw_clear_mem(img);
            return nullptr;
        }

        jint *pixels = env->GetIntArrayElements(result, nullptr);
        if (pixels == nullptr) {
            LOGE("decodeRaw: GetIntArrayElements failed");
            g_lastDecodeError = 4; // OOM
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

        g_lastDecodeError = 0; // SUCCESS
        return result;
    } catch (const std::bad_alloc &e) {
        LOGE("decodeRaw: C++ OOM: %s", e.what());
        g_lastDecodeError = 4; // OOM
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    } catch (const std::exception &e) {
        LOGE("decodeRaw: C++ exception: %s", e.what());
        g_lastDecodeError = 3; // DECODE_FAILED
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    } catch (...) {
        LOGE("decodeRaw: unknown C++ exception");
        g_lastDecodeError = 3; // DECODE_FAILED
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

JNIEXPORT jint JNICALL
Java_com_rapidraw_core_RawDecoder_getDecodeError(
    JNIEnv *env,
    jobject /*thiz*/
) {
    (void)env; // unused
    return static_cast<jint>(g_lastDecodeError);
}

} // extern "C"
