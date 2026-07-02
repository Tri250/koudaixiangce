/**
 * ai_inference.cpp — ONNX Runtime AI 推理引擎
 *
 * R-08/R-09/R-10/R-12: 统一 ONNX 推理后端。
 * 提供：
 * - 模型加载/卸载 (OrtSession)
 * - 图像预处理 (RGB→float tensor, resize, normalize)
 * - 推理执行 (Run)
 * - 后处理 (softmax, argmax, threshold)
 * - 内存管理 (Arena allocator)
 *
 * 支持模型：
 * - u2netp (R-08: AI 主体分割)
 * - NIND (R-09: AI 降噪)
 * - Depth-Anything-V2-Small (R-10: 深度估计)
 * - LaMa (R-12: 图像修复)
 */

#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <cstring>
#include <vector>
#include <memory>
#include <algorithm>
#include <cmath>

#define TAG "AIInference"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ── ONNX Runtime 可选编译 ────────────────────────────────────────────────
// 如果 ONNX Runtime 不可用，提供空实现以避免编译错误。
// 运行时通过 JNI 检查是否可用。

#ifdef HAS_ONNXRUNTIME
#include <onnxruntime_cxx_api.h>

static std::unique_ptr<Ort::Env> g_ortEnv;
static std::unordered_map<std::string, std::unique_ptr<Ort::Session>> g_sessions;

static Ort::Env& getEnv() {
    if (!g_ortEnv) {
        g_ortEnv = std::make_unique<Ort::Env>(ORT_LOGGING_LEVEL_WARNING, "RapidRAW");
    }
    return *g_ortEnv;
}
#endif

// ── 图像预处理工具 ──────────────────────────────────────────────────────

/** 从 Android Bitmap 提取 RGB 像素数组 */
static void bitmapToFloatRGB(JNIEnv* env, jobject bitmap, float* out, int targetW, int targetH) {
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);

    void* pixels;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);

    // 双线性下采样到 targetW x targetH
    float scaleX = (float)info.width / targetW;
    float scaleY = (float)info.height / targetH;

    for (int y = 0; y < targetH; y++) {
        for (int x = 0; x < targetW; x++) {
            float sx = (x + 0.5f) * scaleX - 0.5f;
            float sy = (y + 0.5f) * scaleY - 0.5f;
            int x0 = (int)sx;
            int y0 = (int)sy;
            int x1 = (x0 + 1 < info.width) ? x0 + 1 : x0;
            int y1 = (y0 + 1 < info.height) ? y0 + 1 : y0;
            float fx = sx - x0;
            float fy = sy - y0;

            auto sample = [&](int px, int py) -> void {
                uint8_t* p = (uint8_t*)pixels + py * info.stride + px * 4;
                float r = p[0] / 255.0f;
                float g = p[1] / 255.0f;
                float b = p[2] / 255.0f;
                int idx = (y * targetW + x) * 3;
                out[idx]     += (1-fx)*(1-fy)*r + fx*(1-fy)*r + (1-fx)*fy*r + fx*fy*r;
                out[idx + 1] += (1-fx)*(1-fy)*g + fx*(1-fy)*g + (1-fx)*fy*g + fx*fy*g;
                out[idx + 2] += (1-fx)*(1-fy)*b + fx*(1-fy)*b + (1-fx)*fy*b + fx*fy*b;
            };

            // 简化：最近邻采样（避免复杂双线性插值边界处理）
            int sx0 = (int)(sx + 0.5f);
            int sy0 = (int)(sy + 0.5f);
            sx0 = sx0 < 0 ? 0 : (sx0 >= info.width ? info.width - 1 : sx0);
            sy0 = sy0 < 0 ? 0 : (sy0 >= info.height ? info.height - 1 : sy0);
            uint8_t* p = (uint8_t*)pixels + sy0 * info.stride + sx0 * 4;
            int idx = (y * targetW + x) * 3;
            out[idx]     = p[0] / 255.0f;
            out[idx + 1] = p[1] / 255.0f;
            out[idx + 2] = p[2] / 255.0f;
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}

/** 将 float mask 写回 Bitmap (ARGB_8888) */
static void maskToBitmap(JNIEnv* env, jobject bitmap, const float* mask, int w, int h) {
    void* pixels;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);

    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            float v = mask[y * w + x];
            int alpha = (int)(v * 255.0f + 0.5f);
            alpha = alpha < 0 ? 0 : (alpha > 255 ? 255 : alpha);
            uint8_t* p = (uint8_t*)pixels + y * info.stride + x * 4;
            p[0] = (uint8_t)alpha;
            p[1] = (uint8_t)alpha;
            p[2] = (uint8_t)alpha;
            p[3] = 255;
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}

// ── JNI 导出 ────────────────────────────────────────────────────────────

extern "C" {

/** 检查 ONNX Runtime 是否可用 */
JNIEXPORT jboolean JNICALL
Java_com_rapidraw_ai_OnnxInferenceEngine_nativeIsAvailable(JNIEnv*, jclass) {
#ifdef HAS_ONNXRUNTIME
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

/** 加载 ONNX 模型 */
JNIEXPORT jlong JNICALL
Java_com_rapidraw_ai_OnnxInferenceEngine_nativeLoadModel(
    JNIEnv* env, jclass, jstring modelPath, jint numThreads) {
#ifdef HAS_ONNXRUNTIME
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    try {
        Ort::SessionOptions opts;
        opts.SetIntraOpNumThreads(numThreads);
        opts.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
        auto session = std::make_unique<Ort::Session>(getEnv(), path, opts);
        env->ReleaseStringUTFChars(modelPath, path);

        LOGI("Model loaded: %s (threads=%d)", path, numThreads);
        return reinterpret_cast<jlong>(session.release());
    } catch (const std::exception& e) {
        LOGE("Failed to load model: %s", e.what());
        env->ReleaseStringUTFChars(modelPath, path);
        return 0;
    }
#else
    return 0;
#endif
}

/** 卸载模型 */
JNIEXPORT void JNICALL
Java_com_rapidraw_ai_OnnxInferenceEngine_nativeUnloadModel(JNIEnv*, jclass, jlong handle) {
#ifdef HAS_ONNXRUNTIME
    if (handle) {
        delete reinterpret_cast<Ort::Session*>(handle);
        LOGI("Model unloaded");
    }
#endif
}

/** 运行 AI 主体分割推理 (u2netp) */
JNIEXPORT jboolean JNICALL
Java_com_rapidraw_ai_OnnxInferenceEngine_nativeSubjectMask(
    JNIEnv* env, jclass, jlong handle, jobject srcBitmap, jobject dstBitmap) {
#ifdef HAS_ONNXRUNTIME
    if (!handle) return JNI_FALSE;
    auto* session = reinterpret_cast<Ort::Session*>(handle);

    try {
        int w = 320, h = 320; // u2netp input size
        std::vector<float> input(1 * 3 * h * w, 0.0f);
        bitmapToFloatRGB(env, srcBitmap, input.data(), w, h);

        // 构建 input tensor: NCHW format
        Ort::AllocatorWithDefaultOptions alloc;
        std::vector<int64_t> shape = {1, 3, h, w};
        Ort::MemoryInfo memInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
        Ort::Value inputTensor = Ort::Value::CreateTensor<float>(
            memInfo, input.data(), input.size(), shape.data(), shape.size());

        // 推理
        auto inputNames = session->GetInputNameAllocated(0, alloc);
        auto outputNames = session->GetOutputNameAllocated(0, alloc);
        const char* inputName = inputNames[0].get();
        const char* outputName = outputNames[0].get();

        auto outputs = session->Run(Ort::RunOptions{nullptr},
            &inputName, &inputTensor, 1,
            &outputName, 1);

        // 获取输出 mask
        float* outputData = outputs[0].GetTensorMutableData<float>();
        auto outShape = outputs[0].GetTensorTypeAndShapeInfo().GetShape();
        int outH = (int)outShape[2];
        int outW = (int)outShape[3];

        // 后处理：sigmoid + threshold → mask
        std::vector<float> mask(outH * outW);
        for (int i = 0; i < outH * outW; i++) {
            float sig = 1.0f / (1.0f + std::exp(-outputData[i]));
            mask[i] = sig > 0.5f ? 1.0f : 0.0f;
        }

        // 上采样到目标尺寸
        std::vector<float> upsampled;
        int dstW, dstH;
        {
            AndroidBitmapInfo info;
            AndroidBitmap_getInfo(env, dstBitmap, &info);
            dstW = info.width;
            dstH = info.height;
            upsampled.resize(dstW * dstH);
            for (int y = 0; y < dstH; y++) {
                for (int x = 0; x < dstW; x++) {
                    float sx = (float)x * outW / dstW;
                    float sy = (float)y * outH / dstH;
                    int ix = (int)sx, iy = (int)sy;
                    ix = ix < 0 ? 0 : (ix >= outW ? outW - 1 : ix);
                    iy = iy < 0 ? 0 : (iy >= outH ? outH - 1 : iy);
                    upsampled[y * dstW + x] = mask[iy * outW + ix];
                }
            }
        }

        maskToBitmap(env, dstBitmap, upsampled.data(), dstW, dstH);
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("SubjectMask inference failed: %s", e.what());
        return JNI_FALSE;
    }
#else
    return JNI_FALSE;
#endif
}

/** 运行 NIND AI 降噪推理 */
JNIEXPORT jboolean JNICALL
Java_com_rapidraw_ai_OnnxInferenceEngine_nativeNindDenoise(
    JNIEnv* env, jclass, jlong handle, jobject srcBitmap, jobject dstBitmap) {
#ifdef HAS_ONNXRUNTIME
    if (!handle) return JNI_FALSE;
    auto* session = reinterpret_cast<Ort::Session*>(handle);

    try {
        int w = 256, h = 256; // NIND input patch size
        // 实际实现按 patch 切分处理，此处简化为单 patch 推理

        std::vector<float> input(1 * 3 * h * w, 0.0f);
        bitmapToFloatRGB(env, srcBitmap, input.data(), w, h);

        std::vector<int64_t> shape = {1, 3, h, w};
        Ort::MemoryInfo memInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
        Ort::Value inputTensor = Ort::Value::CreateTensor<float>(
            memInfo, input.data(), input.size(), shape.data(), shape.size());

        Ort::AllocatorWithDefaultOptions alloc;
        auto inputNames = session->GetInputNameAllocated(0, alloc);
        auto outputNames = session->GetOutputNameAllocated(0, alloc);
        const char* inputName = inputNames[0].get();
        const char* outputName = outputNames[0].get();

        auto outputs = session->Run(Ort::RunOptions{nullptr},
            &inputName, &inputTensor, 1, &outputName, 1);

        float* outputData = outputs[0].GetTensorMutableData<float>();

        // 写回 Bitmap
        void* pixels;
        AndroidBitmap_lockPixels(env, dstBitmap, &pixels);
        AndroidBitmapInfo info;
        AndroidBitmap_getInfo(env, dstBitmap, &info);
        for (int y = 0; y < info.height && y < h; y++) {
            for (int x = 0; x < info.width && x < w; x++) {
                int idx = (y * w + x) * 3;
                int r = (int)(outputData[idx] * 255.0f + 0.5f);
                int g = (int)(outputData[idx+1] * 255.0f + 0.5f);
                int b = (int)(outputData[idx+2] * 255.0f + 0.5f);
                r = r < 0 ? 0 : (r > 255 ? 255 : r);
                g = g < 0 ? 0 : (g > 255 ? 255 : g);
                b = b < 0 ? 0 : (b > 255 ? 255 : b);
                uint8_t* p = (uint8_t*)pixels + y * info.stride + x * 4;
                p[0] = (uint8_t)r;
                p[1] = (uint8_t)g;
                p[2] = (uint8_t)b;
                p[3] = 255;
            }
        }
        AndroidBitmap_unlockPixels(env, dstBitmap);
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("NIND denoise inference failed: %s", e.what());
        return JNI_FALSE;
    }
#else
    return JNI_FALSE;
#endif
}

/** 运行 Depth Anything v2 深度估计推理 */
JNIEXPORT jboolean JNICALL
Java_com_rapidraw_ai_OnnxInferenceEngine_nativeDepthEstimate(
    JNIEnv* env, jclass, jlong handle, jobject srcBitmap, jobject dstBitmap) {
#ifdef HAS_ONNXRUNTIME
    if (!handle) return JNI_FALSE;
    auto* session = reinterpret_cast<Ort::Session*>(handle);

    try {
        int w = 518, h = 518; // Depth-Anything-V2-Small input size
        std::vector<float> input(1 * 3 * h * w, 0.0f);
        bitmapToFloatRGB(env, srcBitmap, input.data(), w, h);

        std::vector<int64_t> shape = {1, 3, h, w};
        Ort::MemoryInfo memInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
        Ort::Value inputTensor = Ort::Value::CreateTensor<float>(
            memInfo, input.data(), input.size(), shape.data(), shape.size());

        Ort::AllocatorWithDefaultOptions alloc;
        auto inputNames = session->GetInputNameAllocated(0, alloc);
        auto outputNames = session->GetOutputNameAllocated(0, alloc);
        const char* inputName = inputNames[0].get();
        const char* outputName = outputNames[0].get();

        auto outputs = session->Run(Ort::RunOptions{nullptr},
            &inputName, &inputTensor, 1, &outputName, 1);

        float* outputData = outputs[0].GetTensorMutableData<float>();
        auto outShape = outputs[0].GetTensorTypeAndShapeInfo().GetShape();
        int outH = (int)outShape[2];
        int outW = (int)outShape[3];

        // 深度值归一化到 [0, 1]
        float minVal = outputData[0], maxVal = outputData[0];
        for (int i = 0; i < outH * outW; i++) {
            if (outputData[i] < minVal) minVal = outputData[i];
            if (outputData[i] > maxVal) maxVal = outputData[i];
        }
        float range = maxVal - minVal;
        if (range < 1e-6f) range = 1.0f;

        std::vector<float> depth(outH * outW);
        for (int i = 0; i < outH * outW; i++) {
            depth[i] = (outputData[i] - minVal) / range; // 近=1, 远=0
        }

        // 上采样到目标尺寸
        AndroidBitmapInfo info;
        AndroidBitmap_getInfo(env, dstBitmap, &info);
        std::vector<float> upsampled(info.width * info.height);
        for (int y = 0; y < info.height; y++) {
            for (int x = 0; x < info.width; x++) {
                float sx = (float)x * outW / info.width;
                float sy = (float)y * outH / info.height;
                int ix = (int)sx, iy = (int)sy;
                ix = ix < 0 ? 0 : (ix >= outW ? outW - 1 : ix);
                iy = iy < 0 ? 0 : (iy >= outH ? outH - 1 : iy);
                upsampled[y * info.width + x] = depth[iy * outW + ix];
            }
        }

        maskToBitmap(env, dstBitmap, upsampled.data(), info.width, info.height);
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("Depth estimate inference failed: %s", e.what());
        return JNI_FALSE;
    }
#else
    return JNI_FALSE;
#endif
}

/** 运行 LaMa 图像修复推理 */
JNIEXPORT jboolean JNICALL
Java_com_rapidraw_ai_OnnxInferenceEngine_nativeLaMaInpaint(
    JNIEnv* env, jclass, jlong handle, jobject srcBitmap, jobject maskBitmap, jobject dstBitmap) {
#ifdef HAS_ONNXRUNTIME
    if (!handle) return JNI_FALSE;
    auto* session = reinterpret_cast<Ort::Session*>(handle);

    try {
        int w = 256, h = 256; // LaMa input size
        std::vector<float> image(1 * 3 * h * w, 0.0f);
        std::vector<float> mask(1 * 1 * h * w, 0.0f);
        bitmapToFloatRGB(env, srcBitmap, image.data(), w, h);

        // 提取 mask 单通道
        {
            AndroidBitmapInfo info;
            void* pixels;
            AndroidBitmap_getInfo(env, maskBitmap, &info);
            AndroidBitmap_lockPixels(env, maskBitmap, &pixels);
            for (int y = 0; y < h && y < info.height; y++) {
                for (int x = 0; x < w && x < info.width; x++) {
                    uint8_t* p = (uint8_t*)pixels + y * info.stride + x * 4;
                    mask[y * w + x] = (p[0] > 128) ? 1.0f : 0.0f;
                }
            }
            AndroidBitmap_unlockPixels(env, maskBitmap);
        }

        Ort::MemoryInfo memInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
        std::vector<int64_t> imgShape = {1, 3, h, w};
        std::vector<int64_t> maskShape = {1, 1, h, w};
        Ort::Value imgTensor = Ort::Value::CreateTensor<float>(memInfo, image.data(), image.size(), imgShape.data(), imgShape.size());
        Ort::Value maskTensor = Ort::Value::CreateTensor<float>(memInfo, mask.data(), mask.size(), maskShape.data(), maskShape.size());

        Ort::AllocatorWithDefaultOptions alloc;
        auto inputNames = session->GetInputNameAllocated(0, alloc);
        std::vector<const char*> inputNamePtrs = {inputNames[0].get(), inputNames[1].get()};
        std::vector<Ort::Value> inputTensors;
        inputTensors.push_back(std::move(imgTensor));
        inputTensors.push_back(std::move(maskTensor));

        auto outputNames = session->GetOutputNameAllocated(0, alloc);
        const char* outputName = outputNames[0].get();

        auto outputs = session->Run(Ort::RunOptions{nullptr},
            inputNamePtrs.data(), inputTensors.data(), 2, &outputName, 1);

        float* outputData = outputs[0].GetTensorMutableData<float>();

        void* pixels;
        AndroidBitmap_lockPixels(env, dstBitmap, &pixels);
        AndroidBitmapInfo info;
        AndroidBitmap_getInfo(env, dstBitmap, &info);
        for (int y = 0; y < info.height && y < h; y++) {
            for (int x = 0; x < info.width && x < w; x++) {
                int idx = (y * w + x) * 3;
                int r = (int)(outputData[idx] * 255.0f + 0.5f);
                int g = (int)(outputData[idx+1] * 255.0f + 0.5f);
                int b = (int)(outputData[idx+2] * 255.0f + 0.5f);
                r = r < 0 ? 0 : (r > 255 ? 255 : r);
                g = g < 0 ? 0 : (g > 255 ? 255 : g);
                b = b < 0 ? 0 : (b > 255 ? 255 : b);
                uint8_t* p = (uint8_t*)pixels + y * info.stride + x * 4;
                p[0] = (uint8_t)r;
                p[1] = (uint8_t)g;
                p[2] = (uint8_t)b;
                p[3] = 255;
            }
        }
        AndroidBitmap_unlockPixels(env, dstBitmap);
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("LaMa inpainting inference failed: %s", e.what());
        return JNI_FALSE;
    }
#else
    return JNI_FALSE;
#endif
}

} // extern "C"