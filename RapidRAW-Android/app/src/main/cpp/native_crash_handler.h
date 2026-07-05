#ifndef NATIVE_CRASH_HANDLER_H
#define NATIVE_CRASH_HANDLER_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jboolean JNICALL
Java_com_rapidraw_core_NativeCrashHandler_installNativeHandler(
    JNIEnv *env,
    jobject thiz,
    jstring crashLogDir,
    jstring appVersion,
    jstring buildFingerprint
);

JNIEXPORT jstring JNICALL
Java_com_rapidraw_core_NativeCrashHandler_getCrashLogs(
    JNIEnv *env,
    jobject thiz
);

#ifdef __cplusplus
}
#endif

#endif // NATIVE_CRASH_HANDLER_H