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

// SAM (Segment Anything Model) image embedding cache.
// Encoder 输出 [1, 256, 64, 64] + 原图尺寸（供 decoder 坐标转换使用）。
// 缓存于 native 堆以避免 Java GC，多次 decode 共享同一 embedding。
struct SamEmbedding {
    std::vector<float> data;  // image_embedding, shape [1, 256, 64, 64]
    int origWidth;
    int origHeight;
};

// SAM encoder 输入尺寸（固定 1024×1024，letterbox 保持比例 + 黑色填充）。
// 与原 RapidRAW (Rust) 实现一致。
static constexpr int SAM_INPUT_SIZE = 1024;

// CLIP ViT-B/32 图像 encoder 输入尺寸（固定 224×224）。
static constexpr int CLIP_INPUT_SIZE = 224;
// CLIP ViT-B/32 嵌入维度。
static constexpr int CLIP_EMBED_DIM = 512;
#endif

// ── 图像预处理工具 ──────────────────────────────────────────────────────

/** 从 Android Bitmap 提取 RGB 像素数组，双线性下采样到 targetW×targetH。
 *  输出 NCHW planar float [0,1]：out[c*targetW*targetH + y*targetW + x]。
 *  边界采用 clamp（取最近的有效边缘像素），不返回 0。
 *  与 ONNX tensor shape {1,3,H,W} 的内存布局一致，调用方无需再做布局转换。 */
static void bitmapToFloatRGB(JNIEnv* env, jobject bitmap, float* out, int targetW, int targetH) {
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return;
    }

    const int srcW = (int)info.width;
    const int srcH = (int)info.height;
    const uint32_t stride = info.stride;
    const size_t planeSize = (size_t)targetW * targetH;

    const float scaleX = (float)srcW / targetW;
    const float scaleY = (float)srcH / targetH;

    for (int y = 0; y < targetH; y++) {
        float sy = (y + 0.5f) * scaleY - 0.5f;
        int y0 = (int)floorf(sy);
        int y1 = y0 + 1;
        float fy = sy - (float)y0;
        if (y0 < 0) { y0 = 0; fy = 0.0f; }
        if (y0 > srcH - 1) y0 = srcH - 1;
        if (y1 < 0) y1 = 0;
        if (y1 > srcH - 1) y1 = srcH - 1;

        for (int x = 0; x < targetW; x++) {
            float sx = (x + 0.5f) * scaleX - 0.5f;
            int x0 = (int)floorf(sx);
            int x1 = x0 + 1;
            float fx = sx - (float)x0;
            if (x0 < 0) { x0 = 0; fx = 0.0f; }
            if (x0 > srcW - 1) x0 = srcW - 1;
            if (x1 < 0) x1 = 0;
            if (x1 > srcW - 1) x1 = srcW - 1;

            // 4 个角点分别采样（修复原 bug：原来重复使用同一像素的 r/g/b）
            const uint8_t* p00 = (const uint8_t*)pixels + (size_t)y0 * stride + (size_t)x0 * 4;
            const uint8_t* p01 = (const uint8_t*)pixels + (size_t)y0 * stride + (size_t)x1 * 4;
            const uint8_t* p10 = (const uint8_t*)pixels + (size_t)y1 * stride + (size_t)x0 * 4;
            const uint8_t* p11 = (const uint8_t*)pixels + (size_t)y1 * stride + (size_t)x1 * 4;

            const float w00 = (1.0f - fx) * (1.0f - fy);
            const float w01 = fx * (1.0f - fy);
            const float w10 = (1.0f - fx) * fy;
            const float w11 = fx * fy;

            const size_t idx = (size_t)y * targetW + x;
            out[idx]                 = (p00[0] * w00 + p01[0] * w01 + p10[0] * w10 + p11[0] * w11) / 255.0f;
            out[planeSize + idx]     = (p00[1] * w00 + p01[1] * w01 + p10[1] * w10 + p11[1] * w11) / 255.0f;
            out[2 * planeSize + idx] = (p00[2] * w00 + p01[2] * w01 + p10[2] * w10 + p11[2] * w11) / 255.0f;
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}

/** 计算 feather 权重：距 patch 边缘的距离权重（0=边缘, 1=中心），用于平滑拼接。
 *  边距 = min(x, y, w-1-x, h-1-y)；边距 ≥ featherRadius 返回 1，否则返回 边距/featherRadius。 */
static float featherWeight(int x, int y, int w, int h, int featherRadius) {
    if (featherRadius <= 0) return 1.0f;
    int dx = std::min(x, w - 1 - x);
    int dy = std::min(y, h - 1 - y);
    int d = std::min(dx, dy);
    if (d >= featherRadius) return 1.0f;
    return (float)d / (float)featherRadius;
}

/** 从 Bitmap 提取指定矩形区域 (x0,y0)-(x1,y1) 的 RGB float patch，双线性 resize 到 targetW×targetH。
 *  区域为左闭右开 [x0,x1)×[y0,y1)；超出 Bitmap 边界时 clamp 到最近边缘像素（不返回 0）。
 *  输出 NCHW planar float [0,1]：out[c*targetW*targetH + y*targetW + x]。
 *  边界 patch（区域超出图像）通过 clamp 自动填充到 PATCH_SIZE，保证送入模型的尺寸恒定。 */
static void extractPatch(JNIEnv* env, jobject bitmap, int x0, int y0, int x1, int y1,
                         float* out, int targetW, int targetH) {
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return;
    }

    const int srcW = (int)info.width;
    const int srcH = (int)info.height;
    const uint32_t stride = info.stride;
    const int regionW = x1 - x0;
    const int regionH = y1 - y0;
    if (regionW <= 0 || regionH <= 0) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return;
    }

    const size_t planeSize = (size_t)targetW * targetH;
    const float scaleX = (float)regionW / targetW;
    const float scaleY = (float)regionH / targetH;

    for (int y = 0; y < targetH; y++) {
        float sy = (y + 0.5f) * scaleY - 0.5f;  // 区域内坐标
        int ry0 = (int)floorf(sy);
        int ry1 = ry0 + 1;
        float fy = sy - (float)ry0;
        int ay0 = ry0 + y0;  // 转到 Bitmap 绝对坐标
        int ay1 = ry1 + y0;
        if (ay0 < 0) { ay0 = 0; fy = 0.0f; }
        if (ay0 > srcH - 1) ay0 = srcH - 1;
        if (ay1 < 0) ay1 = 0;
        if (ay1 > srcH - 1) ay1 = srcH - 1;

        for (int x = 0; x < targetW; x++) {
            float sx = (x + 0.5f) * scaleX - 0.5f;
            int rx0 = (int)floorf(sx);
            int rx1 = rx0 + 1;
            float fx = sx - (float)rx0;
            int ax0 = rx0 + x0;
            int ax1 = rx1 + x0;
            if (ax0 < 0) { ax0 = 0; fx = 0.0f; }
            if (ax0 > srcW - 1) ax0 = srcW - 1;
            if (ax1 < 0) ax1 = 0;
            if (ax1 > srcW - 1) ax1 = srcW - 1;

            const uint8_t* p00 = (const uint8_t*)pixels + (size_t)ay0 * stride + (size_t)ax0 * 4;
            const uint8_t* p01 = (const uint8_t*)pixels + (size_t)ay0 * stride + (size_t)ax1 * 4;
            const uint8_t* p10 = (const uint8_t*)pixels + (size_t)ay1 * stride + (size_t)ax0 * 4;
            const uint8_t* p11 = (const uint8_t*)pixels + (size_t)ay1 * stride + (size_t)ax1 * 4;

            const float w00 = (1.0f - fx) * (1.0f - fy);
            const float w01 = fx * (1.0f - fy);
            const float w10 = (1.0f - fx) * fy;
            const float w11 = fx * fy;

            const size_t idx = (size_t)y * targetW + x;
            out[idx]                 = (p00[0] * w00 + p01[0] * w01 + p10[0] * w10 + p11[0] * w11) / 255.0f;
            out[planeSize + idx]     = (p00[1] * w00 + p01[1] * w01 + p10[1] * w10 + p11[1] * w11) / 255.0f;
            out[2 * planeSize + idx] = (p00[2] * w00 + p01[2] * w01 + p10[2] * w10 + p11[2] * w11) / 255.0f;
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}

/** 从 maskBitmap 提取单通道 patch，双线性 resize 到 targetW×targetH。
 *  与 extractPatch 使用相同的区域与采样数学，保证与 image patch 像素对齐。
 *  输出单通道 float：源 >128(0.5) → 1.0，否则 0.0。
 *  超出 mask 边界时 clamp 到最近边缘像素。 */
static void extractMaskPatch(JNIEnv* env, jobject maskBitmap, int x0, int y0, int x1, int y1,
                             float* out, int targetW, int targetH) {
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, maskBitmap, &info);

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, maskBitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return;
    }

    const int srcW = (int)info.width;
    const int srcH = (int)info.height;
    const uint32_t stride = info.stride;
    const int regionW = x1 - x0;
    const int regionH = y1 - y0;
    if (regionW <= 0 || regionH <= 0) {
        AndroidBitmap_unlockPixels(env, maskBitmap);
        return;
    }

    const float scaleX = (float)regionW / targetW;
    const float scaleY = (float)regionH / targetH;

    for (int y = 0; y < targetH; y++) {
        float sy = (y + 0.5f) * scaleY - 0.5f;
        int ry0 = (int)floorf(sy);
        int ry1 = ry0 + 1;
        float fy = sy - (float)ry0;
        int ay0 = ry0 + y0;
        int ay1 = ry1 + y0;
        if (ay0 < 0) { ay0 = 0; fy = 0.0f; }
        if (ay0 > srcH - 1) ay0 = srcH - 1;
        if (ay1 < 0) ay1 = 0;
        if (ay1 > srcH - 1) ay1 = srcH - 1;

        for (int x = 0; x < targetW; x++) {
            float sx = (x + 0.5f) * scaleX - 0.5f;
            int rx0 = (int)floorf(sx);
            int rx1 = rx0 + 1;
            float fx = sx - (float)rx0;
            int ax0 = rx0 + x0;
            int ax1 = rx1 + x0;
            if (ax0 < 0) { ax0 = 0; fx = 0.0f; }
            if (ax0 > srcW - 1) ax0 = srcW - 1;
            if (ax1 < 0) ax1 = 0;
            if (ax1 > srcW - 1) ax1 = srcW - 1;

            const uint8_t* p00 = (const uint8_t*)pixels + (size_t)ay0 * stride + (size_t)ax0 * 4;
            const uint8_t* p01 = (const uint8_t*)pixels + (size_t)ay0 * stride + (size_t)ax1 * 4;
            const uint8_t* p10 = (const uint8_t*)pixels + (size_t)ay1 * stride + (size_t)ax0 * 4;
            const uint8_t* p11 = (const uint8_t*)pixels + (size_t)ay1 * stride + (size_t)ax1 * 4;

            const float w00 = (1.0f - fx) * (1.0f - fy);
            const float w01 = fx * (1.0f - fy);
            const float w10 = (1.0f - fx) * fy;
            const float w11 = fx * fy;

            float v = (p00[0] * w00 + p01[0] * w01 + p10[0] * w10 + p11[0] * w11) / 255.0f;
            out[y * targetW + x] = (v > 0.5f) ? 1.0f : 0.0f;
        }
    }

    AndroidBitmap_unlockPixels(env, maskBitmap);
}

/** 将 patch (patchW×patchH float NCHW RGB) 写回 Bitmap 的指定矩形区域，按 feather 权重混合。
 *  目标矩形 [dstX0,dstX1)×[dstY0,dstY1)，patch 内对应坐标 (i,j)∈[0,patchW)×[0,patchH)。
 *  混合：dst = dst*(1-alpha) + patch*alpha，alpha 由 featherWeight(patch 局部坐标) 计算。
 *  边界 patch：只写有效区域 (validX1-validX0)×(validY1-validY0)，patch 缓冲仍为完整 PATCH_SIZE。 */
static void blendPatch(JNIEnv* env, jobject bitmap, const float* patch, int patchW, int patchH,
                       int dstX0, int dstY0, int dstX1, int dstY1, int featherRadius) {
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return;
    }

    const int dstW = (int)info.width;
    const int dstH = (int)info.height;
    const uint32_t stride = info.stride;
    const size_t planeSize = (size_t)patchW * patchH;

    const int writeW = dstX1 - dstX0;
    const int writeH = dstY1 - dstY0;

    for (int j = 0; j < writeH; j++) {
        if (j >= patchH) break;
        int dy = dstY0 + j;
        if (dy < 0 || dy >= dstH) continue;
        for (int i = 0; i < writeW; i++) {
            if (i >= patchW) break;
            int dx = dstX0 + i;
            if (dx < 0 || dx >= dstW) continue;

            float alpha = featherWeight(i, j, patchW, patchH, featherRadius);
            if (alpha <= 0.0f) continue;

            const size_t pIdx = (size_t)j * patchW + i;
            const float pr = patch[pIdx];
            const float pg = patch[planeSize + pIdx];
            const float pb = patch[2 * planeSize + pIdx];

            uint8_t* p = (uint8_t*)pixels + (size_t)dy * stride + (size_t)dx * 4;
            const float dr = p[0] / 255.0f;
            const float dg = p[1] / 255.0f;
            const float db = p[2] / 255.0f;

            float r = dr * (1.0f - alpha) + pr * alpha;
            float g = dg * (1.0f - alpha) + pg * alpha;
            float b = db * (1.0f - alpha) + pb * alpha;

            int ir = (int)(r * 255.0f + 0.5f);
            int ig = (int)(g * 255.0f + 0.5f);
            int ib = (int)(b * 255.0f + 0.5f);
            ir = ir < 0 ? 0 : (ir > 255 ? 255 : ir);
            ig = ig < 0 ? 0 : (ig > 255 ? 255 : ig);
            ib = ib < 0 ? 0 : (ib > 255 ? 255 : ib);

            p[0] = (uint8_t)ir;
            p[1] = (uint8_t)ig;
            p[2] = (uint8_t)ib;
            p[3] = 255;
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

/** 运行 NIND AI 降噪推理。
 *  采用 256×256 patch tiling（50% overlap, feather=64）覆盖整张图：
 *  - 遍历步长 STEP = PATCH_SIZE - OVERLAP = 128
 *  - 每个 patch：extractPatch(clamp 填充到 256×256) → ONNX 推理 → blendPatch(feather 混合)
 *  - dst 初始化为 src 副本，保证图像边缘/未覆盖像素回退到原图而非黑边 */
JNIEXPORT jboolean JNICALL
Java_com_rapidraw_ai_OnnxInferenceEngine_nativeNindDenoise(
    JNIEnv* env, jclass, jlong handle, jobject srcBitmap, jobject dstBitmap) {
#ifdef HAS_ONNXRUNTIME
    if (!handle) return JNI_FALSE;
    auto* session = reinterpret_cast<Ort::Session*>(handle);

    try {
        AndroidBitmapInfo info;
        if (AndroidBitmap_getInfo(env, srcBitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
            LOGE("NIND: failed to get src bitmap info");
            return JNI_FALSE;
        }
        const int W = (int)info.width;
        const int H = (int)info.height;
        if (W <= 0 || H <= 0) return JNI_FALSE;

        // 初始化 dst = src：feather 混合在图像边缘/低权重区域回退到原图，避免黑边
        {
            AndroidBitmapInfo dstInfo;
            AndroidBitmap_getInfo(env, dstBitmap, &dstInfo);
            void* srcPixels = nullptr;
            void* dstPixels = nullptr;
            if (AndroidBitmap_lockPixels(env, srcBitmap, &srcPixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
                LOGE("NIND: failed to lock src pixels");
                return JNI_FALSE;
            }
            if (AndroidBitmap_lockPixels(env, dstBitmap, &dstPixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
                AndroidBitmap_unlockPixels(env, srcBitmap);
                LOGE("NIND: failed to lock dst pixels");
                return JNI_FALSE;
            }
            const uint32_t srcStride = info.stride;
            const uint32_t dstStride = dstInfo.stride;
            const size_t rowBytes = (size_t)W * 4;
            for (int y = 0; y < H; y++) {
                memcpy((uint8_t*)dstPixels + (size_t)y * dstStride,
                       (uint8_t*)srcPixels + (size_t)y * srcStride, rowBytes);
            }
            AndroidBitmap_unlockPixels(env, dstBitmap);
            AndroidBitmap_unlockPixels(env, srcBitmap);
        }

        constexpr int PATCH_SIZE = 256;
        constexpr int OVERLAP = 128;
        constexpr int FEATHER = 64;
        constexpr int STEP = PATCH_SIZE - OVERLAP;  // 128

        std::vector<float> input((size_t)3 * PATCH_SIZE * PATCH_SIZE, 0.0f);

        Ort::MemoryInfo memInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
        std::vector<int64_t> shape = {1, 3, PATCH_SIZE, PATCH_SIZE};
        Ort::AllocatorWithDefaultOptions alloc;
        auto inputNameAlloc = session->GetInputNameAllocated(0, alloc);
        auto outputNameAlloc = session->GetOutputNameAllocated(0, alloc);
        const char* inputName = inputNameAlloc.get();
        const char* outputName = outputNameAlloc.get();

        for (int py = 0; py < H; py += STEP) {
            for (int px = 0; px < W; px += STEP) {
                const int x1 = px + PATCH_SIZE;
                const int y1 = py + PATCH_SIZE;
                // 有效写回区域（边界 patch 不足 PATCH_SIZE 时只写到图像边界）
                const int validX1 = (x1 > W) ? W : x1;
                const int validY1 = (y1 > H) ? H : y1;

                // 提取 patch（区域超出图像时 extractPatch 内部 clamp 到边缘像素，
                // 保证送入模型的输入恒为 PATCH_SIZE×PATCH_SIZE）
                std::fill(input.begin(), input.end(), 0.0f);
                extractPatch(env, srcBitmap, px, py, x1, y1,
                             input.data(), PATCH_SIZE, PATCH_SIZE);

                // ONNX 推理
                Ort::Value inputTensor = Ort::Value::CreateTensor<float>(
                    memInfo, input.data(), input.size(), shape.data(), shape.size());
                auto outputs = session->Run(Ort::RunOptions{nullptr},
                    &inputName, &inputTensor, 1, &outputName, 1);
                float* outputData = outputs[0].GetTensorMutableData<float>();

                // 写回有效区域（feather 混合）
                blendPatch(env, dstBitmap, outputData, PATCH_SIZE, PATCH_SIZE,
                           px, py, validX1, validY1, FEATHER);
            }
        }
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

/** 运行 LaMa 图像修复推理。
 *  采用 256×256 patch tiling（50% overlap, feather=64），与 NIND 相同的覆盖策略，
 *  但每个 patch 额外提取对应的 mask patch [1,1,256,256]（>128→1.0，否则 0.0）：
 *  - 优化：跳过全黑 mask patch（不调用推理，dst 保持 src 副本）
 *  - LaMa 输入 2 个 tensor：image [1,3,256,256] + mask [1,1,256,256]
 *  - 输出 patch 写回时同样用 feather 混合，平滑 patch 间接缝
 *  - dst 初始化为 src 副本：非修复区域保持原图，修复区域由 patch 覆盖 */
JNIEXPORT jboolean JNICALL
Java_com_rapidraw_ai_OnnxInferenceEngine_nativeLaMaInpaint(
    JNIEnv* env, jclass, jlong handle, jobject srcBitmap, jobject maskBitmap, jobject dstBitmap) {
#ifdef HAS_ONNXRUNTIME
    if (!handle) return JNI_FALSE;
    auto* session = reinterpret_cast<Ort::Session*>(handle);

    try {
        AndroidBitmapInfo info;
        if (AndroidBitmap_getInfo(env, srcBitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
            LOGE("LaMa: failed to get src bitmap info");
            return JNI_FALSE;
        }
        const int W = (int)info.width;
        const int H = (int)info.height;
        if (W <= 0 || H <= 0) return JNI_FALSE;

        // 初始化 dst = src：非修复区域保持原图，修复区域由 patch 覆盖
        {
            AndroidBitmapInfo dstInfo;
            AndroidBitmap_getInfo(env, dstBitmap, &dstInfo);
            void* srcPixels = nullptr;
            void* dstPixels = nullptr;
            if (AndroidBitmap_lockPixels(env, srcBitmap, &srcPixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
                LOGE("LaMa: failed to lock src pixels");
                return JNI_FALSE;
            }
            if (AndroidBitmap_lockPixels(env, dstBitmap, &dstPixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
                AndroidBitmap_unlockPixels(env, srcBitmap);
                LOGE("LaMa: failed to lock dst pixels");
                return JNI_FALSE;
            }
            const uint32_t srcStride = info.stride;
            const uint32_t dstStride = dstInfo.stride;
            const size_t rowBytes = (size_t)W * 4;
            for (int y = 0; y < H; y++) {
                memcpy((uint8_t*)dstPixels + (size_t)y * dstStride,
                       (uint8_t*)srcPixels + (size_t)y * srcStride, rowBytes);
            }
            AndroidBitmap_unlockPixels(env, dstBitmap);
            AndroidBitmap_unlockPixels(env, srcBitmap);
        }

        constexpr int PATCH_SIZE = 256;
        constexpr int OVERLAP = 128;
        constexpr int FEATHER = 64;
        constexpr int STEP = PATCH_SIZE - OVERLAP;  // 128

        std::vector<float> imageInput((size_t)3 * PATCH_SIZE * PATCH_SIZE, 0.0f);
        std::vector<float> maskInput((size_t)1 * PATCH_SIZE * PATCH_SIZE, 0.0f);

        Ort::MemoryInfo memInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
        std::vector<int64_t> imgShape = {1, 3, PATCH_SIZE, PATCH_SIZE};
        std::vector<int64_t> maskShape = {1, 1, PATCH_SIZE, PATCH_SIZE};
        Ort::AllocatorWithDefaultOptions alloc;

        // 收集输入/输出名称（保持 AllocatedStringPtr 生命周期）。
        // LaMa 模型输入顺序：image(0) + mask(1)，输出为 inpainted image(0)。
        size_t numInputs = session->GetInputCount();
        std::vector<Ort::AllocatedStringPtr> inNameAllocs;
        std::vector<const char*> inNames;
        inNameAllocs.reserve(numInputs);
        inNames.reserve(numInputs);
        for (size_t i = 0; i < numInputs; i++) {
            inNameAllocs.push_back(session->GetInputNameAllocated(i, alloc));
            inNames.push_back(inNameAllocs.back().get());
        }
        size_t numOutputs = session->GetOutputCount();
        std::vector<Ort::AllocatedStringPtr> outNameAllocs;
        std::vector<const char*> outNames;
        outNameAllocs.reserve(numOutputs);
        outNames.reserve(numOutputs);
        for (size_t i = 0; i < numOutputs; i++) {
            outNameAllocs.push_back(session->GetOutputNameAllocated(i, alloc));
            outNames.push_back(outNameAllocs.back().get());
        }

        for (int py = 0; py < H; py += STEP) {
            for (int px = 0; px < W; px += STEP) {
                const int x1 = px + PATCH_SIZE;
                const int y1 = py + PATCH_SIZE;
                const int validX1 = (x1 > W) ? W : x1;
                const int validY1 = (y1 > H) ? H : y1;

                // 先提取 mask patch，判断是否包含修复区域
                std::fill(maskInput.begin(), maskInput.end(), 0.0f);
                extractMaskPatch(env, maskBitmap, px, py, x1, y1,
                                 maskInput.data(), PATCH_SIZE, PATCH_SIZE);

                // 优化：跳过全黑 mask patch（不调用推理，dst 保持 src 副本）
                bool hasMask = false;
                const size_t patchPixels = (size_t)PATCH_SIZE * PATCH_SIZE;
                for (size_t i = 0; i < patchPixels; i++) {
                    if (maskInput[i] > 0.5f) { hasMask = true; break; }
                }
                if (!hasMask) continue;

                // 提取 image patch（与 mask patch 同区域，保证像素对齐）
                std::fill(imageInput.begin(), imageInput.end(), 0.0f);
                extractPatch(env, srcBitmap, px, py, x1, y1,
                             imageInput.data(), PATCH_SIZE, PATCH_SIZE);

                // ONNX 推理（image + mask）
                Ort::Value imgTensor = Ort::Value::CreateTensor<float>(
                    memInfo, imageInput.data(), imageInput.size(),
                    imgShape.data(), imgShape.size());
                Ort::Value maskTensor = Ort::Value::CreateTensor<float>(
                    memInfo, maskInput.data(), maskInput.size(),
                    maskShape.data(), maskShape.size());
                std::vector<Ort::Value> inputTensors;
                inputTensors.reserve(2);
                inputTensors.push_back(std::move(imgTensor));
                inputTensors.push_back(std::move(maskTensor));

                auto outputs = session->Run(Ort::RunOptions{nullptr},
                    inNames.data(), inputTensors.data(), inputTensors.size(),
                    outNames.data(), outNames.size());
                float* outputData = outputs[0].GetTensorMutableData<float>();

                // 写回有效区域（feather 混合）
                blendPatch(env, dstBitmap, outputData, PATCH_SIZE, PATCH_SIZE,
                           px, py, validX1, validY1, FEATHER);
            }
        }
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("LaMa inpainting inference failed: %s", e.what());
        return JNI_FALSE;
    }
#else
    return JNI_FALSE;
#endif
}

// ── SAM (Segment Anything Model) 交互式分割 ──────────────────────────────
// 参照原 RapidRAW (Rust) ai_processing.rs 中的 SAM 部分。
// encoder 一次性计算 image embedding [1, 256, 64, 64]（letterbox 1024×1024 输入），
// decoder 按点/框 prompt 出掩膜。embedding 缓存在 native 层供多次 decode 复用。

/** SAM Encoder: 编码图像 → 返回 embedding handle（native 堆缓存）。
 *  输入 Bitmap 经 letterbox（保持比例 + 黑色填充）缩放到 1024×1024，
 *  以 u8 NCHW tensor 送入 ViT-B encoder。
 *  返回 SamEmbedding* 指针；失败返回 0。 */
JNIEXPORT jlong JNICALL
Java_com_rapidraw_ai_OnnxInferenceEngine_nativeSamEncode(
    JNIEnv* env, jclass, jlong encoderHandle, jobject srcBitmap) {
#ifdef HAS_ONNXRUNTIME
    if (!encoderHandle || !srcBitmap) return 0;
    auto* session = reinterpret_cast<Ort::Session*>(encoderHandle);

    try {
        AndroidBitmapInfo info;
        if (AndroidBitmap_getInfo(env, srcBitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
            LOGE("SAM encode: failed to get bitmap info");
            return 0;
        }
        int origW = (int)info.width;
        int origH = (int)info.height;
        if (origW <= 0 || origH <= 0) return 0;

        // letterbox: 保持比例缩放到 1024，短边填充黑色（左上对齐，与 Rust 参考一致）
        float longSide = (float)std::max(origW, origH);
        float scale = (float)SAM_INPUT_SIZE / longSide;
        int newW = (int)(origW * scale + 0.5f);
        int newH = (int)(origH * scale + 0.5f);
        if (newW > SAM_INPUT_SIZE) newW = SAM_INPUT_SIZE;
        if (newH > SAM_INPUT_SIZE) newH = SAM_INPUT_SIZE;
        if (newW < 1) newW = 1;
        if (newH < 1) newH = 1;

        // NCHW u8 tensor, zero-initialized（黑色填充区域保持 0）
        const int channels = 3;
        std::vector<uint8_t> input((size_t)channels * SAM_INPUT_SIZE * SAM_INPUT_SIZE, 0);

        void* pixels = nullptr;
        if (AndroidBitmap_lockPixels(env, srcBitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
            LOGE("SAM encode: failed to lock pixels");
            return 0;
        }

        // 双线性 resize bitmap → (newW, newH)，写入 NCHW u8 buffer（左上对齐）
        float scaleX = (float)origW / newW;
        float scaleY = (float)origH / newH;
        for (int y = 0; y < newH; y++) {
            float sy = (y + 0.5f) * scaleY - 0.5f;
            int y0 = (int)sy;
            int y1 = (y0 + 1 < origH) ? y0 + 1 : y0;
            float fy = sy - y0;
            if (y0 < 0) { y0 = 0; fy = 0.0f; }
            if (y0 >= origH) y0 = origH - 1;
            if (y1 >= origH) y1 = origH - 1;
            for (int x = 0; x < newW; x++) {
                float sx = (x + 0.5f) * scaleX - 0.5f;
                int x0 = (int)sx;
                int x1 = (x0 + 1 < origW) ? x0 + 1 : x0;
                float fx = sx - x0;
                if (x0 < 0) { x0 = 0; fx = 0.0f; }
                if (x0 >= origW) x0 = origW - 1;
                if (x1 >= origW) x1 = origW - 1;

                uint8_t* p00 = (uint8_t*)pixels + y0 * info.stride + x0 * 4;
                uint8_t* p01 = (uint8_t*)pixels + y0 * info.stride + x1 * 4;
                uint8_t* p10 = (uint8_t*)pixels + y1 * info.stride + x0 * 4;
                uint8_t* p11 = (uint8_t*)pixels + y1 * info.stride + x1 * 4;

                for (int c = 0; c < channels; c++) {
                    float val = (1 - fx) * (1 - fy) * p00[c] + fx * (1 - fy) * p01[c]
                              + (1 - fx) * fy * p10[c] + fx * fy * p11[c];
                    size_t idx = (size_t)c * SAM_INPUT_SIZE * SAM_INPUT_SIZE
                               + (size_t)y * SAM_INPUT_SIZE + x;
                    input[idx] = (uint8_t)(val + 0.5f);
                }
            }
        }
        AndroidBitmap_unlockPixels(env, srcBitmap);

        // 构建 input tensor [1, 3, 1024, 1024] u8（SAM ViT-B encoder 期望 uint8 输入，
        // mean/std 归一化已内置在模型中，与 Rust 参考一致）
        Ort::MemoryInfo memInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
        std::vector<int64_t> shape = {1, channels, SAM_INPUT_SIZE, SAM_INPUT_SIZE};
        Ort::Value inputTensor = Ort::Value::CreateTensor<uint8_t>(
            memInfo, input.data(), input.size(), shape.data(), shape.size());

        Ort::AllocatorWithDefaultOptions alloc;
        auto inputNameAlloc = session->GetInputNameAllocated(0, alloc);
        auto outputNameAlloc = session->GetOutputNameAllocated(0, alloc);
        const char* inputName = inputNameAlloc.get();
        const char* outputName = outputNameAlloc.get();

        auto outputs = session->Run(Ort::RunOptions{nullptr},
            &inputName, &inputTensor, 1, &outputName, 1);

        float* embData = outputs[0].GetTensorMutableData<float>();
        auto embShape = outputs[0].GetTensorTypeAndShapeInfo().GetShape();
        size_t embCount = 1;
        for (auto d : embShape) embCount *= (size_t)d;

        auto* embedding = new SamEmbedding();
        embedding->data.assign(embData, embData + embCount);
        embedding->origWidth = origW;
        embedding->origHeight = origH;

        LOGI("SAM encode: %dx%d -> embedding (%zu floats)", origW, origH, embCount);
        return reinterpret_cast<jlong>(embedding);
    } catch (const std::exception& e) {
        LOGE("SAM encode failed: %s", e.what());
        return 0;
    }
#else
    return 0;
#endif
}

/** SAM Decoder: 输入 embedding + 点/框 prompt → 生成掩膜写入 dstBitmap。
 *  点坐标基于原图坐标系，内部自动转换到 letterbox 后的 1024 空间。
 *  prevMask 为 null 时使用全零 mask_input（首次迭代）。
 *  返回 JNI_TRUE 成功；失败返回 JNI_FALSE。 */
JNIEXPORT jboolean JNICALL
Java_com_rapidraw_ai_OnnxInferenceEngine_nativeSamDecode(
    JNIEnv* env, jclass, jlong decoderHandle, jlong embeddingHandle,
    jfloatArray points, jintArray labels, jfloatArray prevMask, jobject dstBitmap) {
#ifdef HAS_ONNXRUNTIME
    if (!decoderHandle || !embeddingHandle || !points || !labels || !dstBitmap) {
        return JNI_FALSE;
    }
    auto* session = reinterpret_cast<Ort::Session*>(decoderHandle);
    auto* emb = reinterpret_cast<SamEmbedding*>(embeddingHandle);
    if (!session || !emb || emb->data.empty()) return JNI_FALSE;

    try {
        jsize numPoints = env->GetArrayLength(points) / 2;
        jsize numLabels = env->GetArrayLength(labels);
        if (numPoints <= 0 || numPoints != numLabels) {
            LOGE("SAM decode: points/labels length mismatch (%d / %d)", numPoints, numLabels);
            return JNI_FALSE;
        }

        jfloat* ptRaw = env->GetFloatArrayElements(points, nullptr);
        jint* lbRaw = env->GetIntArrayElements(labels, nullptr);

        // 原图 → letterbox 1024 坐标系转换（左上对齐，offset=0，与 encoder 一致）
        float longSide = (float)std::max(emb->origWidth, emb->origHeight);
        float scale = (float)SAM_INPUT_SIZE / longSide;

        // point_coords [1, K, 2], point_labels [1, K]
        std::vector<float> coordsFlat((size_t)numPoints * 2);
        std::vector<float> labelsFlat((size_t)numPoints);
        for (jsize i = 0; i < numPoints; i++) {
            coordsFlat[i * 2]     = ptRaw[i * 2]     * scale;
            coordsFlat[i * 2 + 1] = ptRaw[i * 2 + 1] * scale;
            labelsFlat[i] = (float)lbRaw[i];
        }
        env->ReleaseFloatArrayElements(points, ptRaw, JNI_ABORT);
        env->ReleaseIntArrayElements(labels, lbRaw, JNI_ABORT);

        // mask_input [1, 1, 256, 256] + has_mask_input [1]
        std::vector<float> maskInput((size_t)256 * 256, 0.0f);
        float hasMask = 0.0f;
        if (prevMask != nullptr) {
            jsize pmLen = env->GetArrayLength(prevMask);
            if (pmLen > 0) {
                jfloat* pm = env->GetFloatArrayElements(prevMask, nullptr);
                jsize copyLen = std::min(pmLen, (jsize)(256 * 256));
                for (jsize i = 0; i < copyLen; i++) maskInput[i] = pm[i];
                env->ReleaseFloatArrayElements(prevMask, pm, JNI_ABORT);
                hasMask = 1.0f;
            }
        }

        // orig_im_size [2] = [H, W]（与 Rust 参考一致：height 在前）
        float origSizeArr[2] = { (float)emb->origHeight, (float)emb->origWidth };

        // 构建 6 个输入 tensor
        Ort::MemoryInfo memInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
        std::vector<int64_t> embShape = {1, 256, 64, 64};
        std::vector<int64_t> coordsShape = {1, (int64_t)numPoints, 2};
        std::vector<int64_t> labelsShape = {1, (int64_t)numPoints};
        std::vector<int64_t> maskShape = {1, 1, 256, 256};
        std::vector<int64_t> hasMaskShape = {1};
        std::vector<int64_t> origSizeShape = {2};

        std::vector<Ort::Value> inputs;
        inputs.push_back(Ort::Value::CreateTensor<float>(
            memInfo, emb->data.data(), emb->data.size(), embShape.data(), embShape.size()));
        inputs.push_back(Ort::Value::CreateTensor<float>(
            memInfo, coordsFlat.data(), coordsFlat.size(), coordsShape.data(), coordsShape.size()));
        inputs.push_back(Ort::Value::CreateTensor<float>(
            memInfo, labelsFlat.data(), labelsFlat.size(), labelsShape.data(), labelsShape.size()));
        inputs.push_back(Ort::Value::CreateTensor<float>(
            memInfo, maskInput.data(), maskInput.size(), maskShape.data(), maskShape.size()));
        inputs.push_back(Ort::Value::CreateTensor<float>(
            memInfo, &hasMask, 1, hasMaskShape.data(), hasMaskShape.size()));
        inputs.push_back(Ort::Value::CreateTensor<float>(
            memInfo, origSizeArr, 2, origSizeShape.data(), origSizeShape.size()));

        // 收集输入/输出名称（保持 AllocatedStringPtr 生命周期）
        Ort::AllocatorWithDefaultOptions alloc;
        size_t numInputs = session->GetInputCount();
        std::vector<Ort::AllocatedStringPtr> inNameAllocs;
        std::vector<const char*> inNames;
        inNameAllocs.reserve(numInputs);
        inNames.reserve(numInputs);
        for (size_t i = 0; i < numInputs; i++) {
            inNameAllocs.push_back(session->GetInputNameAllocated(i, alloc));
            inNames.push_back(inNameAllocs.back().get());
        }
        size_t numOutputs = session->GetOutputCount();
        std::vector<Ort::AllocatedStringPtr> outNameAllocs;
        std::vector<const char*> outNames;
        outNameAllocs.reserve(numOutputs);
        outNames.reserve(numOutputs);
        for (size_t i = 0; i < numOutputs; i++) {
            outNameAllocs.push_back(session->GetOutputNameAllocated(i, alloc));
            outNames.push_back(outNameAllocs.back().get());
        }

        auto outputs = session->Run(Ort::RunOptions{nullptr},
            inNames.data(), inputs.data(), numInputs,
            outNames.data(), numOutputs);

        // 定位 masks [1, 3, H, W]（4 维）与 scores [1, 3]（2 维）
        float* masksData = nullptr;
        float* scoresData = nullptr;
        int maskH = 0, maskW = 0;
        for (size_t i = 0; i < outputs.size(); i++) {
            auto shp = outputs[i].GetTensorTypeAndShapeInfo().GetShape();
            if (shp.size() == 4 && masksData == nullptr) {
                masksData = outputs[i].GetTensorMutableData<float>();
                maskH = (int)shp[2];
                maskW = (int)shp[3];
            } else if (shp.size() == 2 && scoresData == nullptr) {
                scoresData = outputs[i].GetTensorMutableData<float>();
            }
        }
        if (!masksData || maskH <= 0 || maskW <= 0) {
            LOGE("SAM decode: no mask output found");
            return JNI_FALSE;
        }

        // 选 score 最高的候选 mask（3 个候选）
        int numCandidates = 3;
        int bestIdx = 0;
        if (scoresData) {
            float bestScore = scoresData[0];
            for (int i = 1; i < numCandidates; i++) {
                if (scoresData[i] > bestScore) {
                    bestScore = scoresData[i];
                    bestIdx = i;
                }
            }
        }

        // 阈值化：logit > 0 → 前景（1.0），否则背景（0.0）
        size_t maskArea = (size_t)maskH * maskW;
        std::vector<float> mask(maskArea);
        for (size_t i = 0; i < maskArea; i++) {
            float v = masksData[(size_t)bestIdx * maskArea + i];
            mask[i] = v > 0.0f ? 1.0f : 0.0f;
        }

        // 写入 dstBitmap（白色=前景）；若输出尺寸 ≠ 目标尺寸则最近邻 resize
        AndroidBitmapInfo dstInfo;
        AndroidBitmap_getInfo(env, dstBitmap, &dstInfo);
        int dstW = (int)dstInfo.width;
        int dstH = (int)dstInfo.height;
        void* dstPixels = nullptr;
        AndroidBitmap_lockPixels(env, dstBitmap, &dstPixels);

        for (int y = 0; y < dstH; y++) {
            int my = (int)((float)y * maskH / dstH);
            if (my < 0) my = 0;
            if (my >= maskH) my = maskH - 1;
            for (int x = 0; x < dstW; x++) {
                int mx = (int)((float)x * maskW / dstW);
                if (mx < 0) mx = 0;
                if (mx >= maskW) mx = maskW - 1;
                uint8_t val = mask[(size_t)my * maskW + mx] > 0.5f ? 255 : 0;
                uint8_t* p = (uint8_t*)dstPixels + y * dstInfo.stride + x * 4;
                p[0] = val;
                p[1] = val;
                p[2] = val;
                p[3] = 255;
            }
        }
        AndroidBitmap_unlockPixels(env, dstBitmap);

        LOGI("SAM decode: %d points -> mask %dx%d (best=%d)", numPoints, dstW, dstH, bestIdx);
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("SAM decode failed: %s", e.what());
        return JNI_FALSE;
    }
#else
    return JNI_FALSE;
#endif
}

/** 释放 SAM embedding（native 堆内存） */
JNIEXPORT void JNICALL
Java_com_rapidraw_ai_OnnxInferenceEngine_nativeSamReleaseEmbedding(
    JNIEnv*, jclass, jlong embeddingHandle) {
#ifdef HAS_ONNXRUNTIME
    if (embeddingHandle) {
        auto* emb = reinterpret_cast<SamEmbedding*>(embeddingHandle);
        delete emb;
        LOGI("SAM embedding released");
    }
#endif
}

/** 查询 SAM embedding 对应的原图尺寸 [width, height]（供 Kotlin 创建输出 Bitmap） */
JNIEXPORT jintArray JNICALL
Java_com_rapidraw_ai_OnnxInferenceEngine_nativeSamGetEmbeddingSize(
    JNIEnv* env, jclass, jlong embeddingHandle) {
    jint dims[2] = {0, 0};
#ifdef HAS_ONNXRUNTIME
    if (embeddingHandle) {
        auto* emb = reinterpret_cast<SamEmbedding*>(embeddingHandle);
        dims[0] = emb->origWidth;
        dims[1] = emb->origHeight;
    }
#endif
    jintArray result = env->NewIntArray(2);
    if (result) env->SetIntArrayRegion(result, 0, 2, dims);
    return result;
}

// ── CLIP Zero-shot 图像分类 ─────────────────────────────────────────────
// 参照原 RapidRAW (Rust) CLIP image encoder 部分。
// 输入 Bitmap → 双线性 resize 224×224 → RGB float NCHW → mean/std 归一化 →
// ONNX image encoder 推理 → 输出 [1, 512] 图像嵌入。
// Kotlin 侧用预计算的标签文本嵌入做余弦相似度匹配（zero-shot 分类）。

/** 运行 CLIP 图像 encoder 推理，输出 512 维图像嵌入。
 *  输入 Bitmap → resize 224×224 → RGB float NCHW → mean/std 归一化 → ONNX 推理 → 512-d embedding。
 *  embedding 写入 jfloatArray 输出参数（Kotlin 侧预分配 512 长度）。
 *  返回 JNI_TRUE 成功。 */
JNIEXPORT jboolean JNICALL
Java_com_rapidraw_ai_OnnxInferenceEngine_nativeClipEncode(
    JNIEnv* env, jclass, jlong handle, jobject srcBitmap, jfloatArray outEmbedding) {
#ifdef HAS_ONNXRUNTIME
    if (!handle || !srcBitmap || !outEmbedding) return JNI_FALSE;
    auto* session = reinterpret_cast<Ort::Session*>(handle);

    jsize outLen = env->GetArrayLength(outEmbedding);
    if (outLen < CLIP_EMBED_DIM) {
        LOGE("CLIP encode: outEmbedding length %d < %d", outLen, CLIP_EMBED_DIM);
        return JNI_FALSE;
    }

    try {
        AndroidBitmapInfo info;
        if (AndroidBitmap_getInfo(env, srcBitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
            LOGE("CLIP encode: failed to get bitmap info");
            return JNI_FALSE;
        }
        int origW = (int)info.width;
        int origH = (int)info.height;
        if (origW <= 0 || origH <= 0) return JNI_FALSE;

        // 双线性 resize bitmap → 224×224，写入 NCHW float buffer（mean/std 归一化）
        // CLIP ViT-B/32 期望 RGB NCHW float32，归一化：
        //   mean = [0.485, 0.456, 0.406], std = [0.229, 0.224, 0.225]
        //   value = (pixel/255.0 - mean) / std
        static const float clipMean[3] = {0.485f, 0.456f, 0.406f};
        static const float clipStd[3]  = {0.229f, 0.224f, 0.225f};

        const int targetW = CLIP_INPUT_SIZE;
        const int targetH = CLIP_INPUT_SIZE;
        // NCHW layout: [1, 3, 224, 224]
        std::vector<float> input((size_t)3 * targetW * targetH, 0.0f);

        void* pixels = nullptr;
        if (AndroidBitmap_lockPixels(env, srcBitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
            LOGE("CLIP encode: failed to lock pixels");
            return JNI_FALSE;
        }

        // 双线性插值（参考 SAM encoder 实现，line 525-559）
        float scaleX = (float)origW / targetW;
        float scaleY = (float)origH / targetH;
        const size_t planeSize = (size_t)targetW * targetH;
        for (int y = 0; y < targetH; y++) {
            float sy = (y + 0.5f) * scaleY - 0.5f;
            int y0 = (int)sy;
            int y1 = (y0 + 1 < origH) ? y0 + 1 : y0;
            float fy = sy - y0;
            if (y0 < 0) { y0 = 0; fy = 0.0f; }
            if (y0 >= origH) y0 = origH - 1;
            if (y1 >= origH) y1 = origH - 1;
            for (int x = 0; x < targetW; x++) {
                float sx = (x + 0.5f) * scaleX - 0.5f;
                int x0 = (int)sx;
                int x1 = (x0 + 1 < origW) ? x0 + 1 : x0;
                float fx = sx - x0;
                if (x0 < 0) { x0 = 0; fx = 0.0f; }
                if (x0 >= origW) x0 = origW - 1;
                if (x1 >= origW) x1 = origW - 1;

                uint8_t* p00 = (uint8_t*)pixels + y0 * info.stride + x0 * 4;
                uint8_t* p01 = (uint8_t*)pixels + y0 * info.stride + x1 * 4;
                uint8_t* p10 = (uint8_t*)pixels + y1 * info.stride + x0 * 4;
                uint8_t* p11 = (uint8_t*)pixels + y1 * info.stride + x1 * 4;

                size_t spatialIdx = (size_t)y * targetW + x;
                for (int c = 0; c < 3; c++) {
                    float val = (1 - fx) * (1 - fy) * p00[c] + fx * (1 - fy) * p01[c]
                              + (1 - fx) * fy * p10[c] + fx * fy * p11[c];
                    float normed = (val / 255.0f - clipMean[c]) / clipStd[c];
                    input[(size_t)c * planeSize + spatialIdx] = normed;
                }
            }
        }
        AndroidBitmap_unlockPixels(env, srcBitmap);

        // 构建 input tensor [1, 3, 224, 224] float32
        Ort::MemoryInfo memInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
        std::vector<int64_t> shape = {1, 3, targetH, targetW};
        Ort::Value inputTensor = Ort::Value::CreateTensor<float>(
            memInfo, input.data(), input.size(), shape.data(), shape.size());

        Ort::AllocatorWithDefaultOptions alloc;
        auto inputNameAlloc = session->GetInputNameAllocated(0, alloc);
        auto outputNameAlloc = session->GetOutputNameAllocated(0, alloc);
        const char* inputName = inputNameAlloc.get();
        const char* outputName = outputNameAlloc.get();

        auto outputs = session->Run(Ort::RunOptions{nullptr},
            &inputName, &inputTensor, 1, &outputName, 1);

        float* embData = outputs[0].GetTensorMutableData<float>();
        auto embShape = outputs[0].GetTensorTypeAndShapeInfo().GetShape();
        // 计算输出元素总数；若输出是 [1, 512] 或 [512] 都正确处理
        size_t embCount = 1;
        for (auto d : embShape) {
            if (d > 0) embCount *= (size_t)d;
        }
        if (embCount == 0) {
            LOGE("CLIP encode: empty output (shape size unknown)");
            return JNI_FALSE;
        }
        // 实际写入维度：取 min(embCount, CLIP_EMBED_DIM)
        size_t writeDim = embCount < (size_t)CLIP_EMBED_DIM ? embCount : (size_t)CLIP_EMBED_DIM;

        // 写回 Kotlin 预分配的 FloatArray（512 长度）
        env->SetFloatArrayRegion(outEmbedding, 0, (jsize)writeDim, embData);
        // 若输出维度不足 512，剩余部分填 0（保持嵌入向量长度一致，便于后续余弦相似度计算）
        if (writeDim < (size_t)CLIP_EMBED_DIM) {
            std::vector<float> zeros(CLIP_EMBED_DIM - writeDim, 0.0f);
            env->SetFloatArrayRegion(outEmbedding, (jsize)writeDim,
                                     (jsize)(CLIP_EMBED_DIM - writeDim), zeros.data());
        }

        LOGI("CLIP encode: %dx%d -> embedding (%zu floats, wrote %zu)",
             origW, origH, embCount, writeDim);
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("CLIP encode failed: %s", e.what());
        return JNI_FALSE;
    }
#else
    return JNI_FALSE;
#endif
}

} // extern "C"