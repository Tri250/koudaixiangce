/**
 * ai_inference_jni.cpp — JNI 注册表
 * 动态注册所有 native 方法。
 */

#include <jni.h>
#include <android/log.h>

#define TAG "AIInferenceJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// 声明外部函数
extern "C" {
jboolean Java_com_rapidraw_ai_OnnxInferenceEngine_nativeIsAvailable(JNIEnv*, jclass);
jlong Java_com_rapidraw_ai_OnnxInferenceEngine_nativeLoadModel(JNIEnv*, jclass, jstring, jint);
void Java_com_rapidraw_ai_OnnxInferenceEngine_nativeUnloadModel(JNIEnv*, jclass, jlong);
jboolean Java_com_rapidraw_ai_OnnxInferenceEngine_nativeSubjectMask(JNIEnv*, jclass, jlong, jobject, jobject);
jboolean Java_com_rapidraw_ai_OnnxInferenceEngine_nativeNindDenoise(JNIEnv*, jclass, jlong, jobject, jobject);
jboolean Java_com_rapidraw_ai_OnnxInferenceEngine_nativeDepthEstimate(JNIEnv*, jclass, jlong, jobject, jobject);
jboolean Java_com_rapidraw_ai_OnnxInferenceEngine_nativeLaMaInpaint(JNIEnv*, jclass, jlong, jobject, jobject, jobject);
}

static JNINativeMethod methods[] = {
    {"nativeIsAvailable", "()Z", (void*)Java_com_rapidraw_ai_OnnxInferenceEngine_nativeIsAvailable},
    {"nativeLoadModel", "(Ljava/lang/String;I)J", (void*)Java_com_rapidraw_ai_OnnxInferenceEngine_nativeLoadModel},
    {"nativeUnloadModel", "(J)V", (void*)Java_com_rapidraw_ai_OnnxInferenceEngine_nativeUnloadModel},
    {"nativeSubjectMask", "(JLandroid/graphics/Bitmap;Landroid/graphics/Bitmap;)Z", (void*)Java_com_rapidraw_ai_OnnxInferenceEngine_nativeSubjectMask},
    {"nativeNindDenoise", "(JLandroid/graphics/Bitmap;Landroid/graphics/Bitmap;)Z", (void*)Java_com_rapidraw_ai_OnnxInferenceEngine_nativeNindDenoise},
    {"nativeDepthEstimate", "(JLandroid/graphics/Bitmap;Landroid/graphics/Bitmap;)Z", (void*)Java_com_rapidraw_ai_OnnxInferenceEngine_nativeDepthEstimate},
    {"nativeLaMaInpaint", "(JLandroid/graphics/Bitmap;Landroid/graphics/Bitmap;Landroid/graphics/Bitmap;)Z", (void*)Java_com_rapidraw_ai_OnnxInferenceEngine_nativeLaMaInpaint},
};

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass clazz = env->FindClass("com/rapidraw/ai/OnnxInferenceEngine");
    if (!clazz) {
        LOGI("OnnxInferenceEngine class not found — AI features disabled");
        return JNI_VERSION_1_6;
    }
    if (env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0])) < 0) {
        LOGI("Failed to register native methods for OnnxInferenceEngine");
    }
    return JNI_VERSION_1_6;
}