# RapidRAW ProGuard Rules

# Room
-keep class androidx.room.** { *; }
-keep class com.rapidraw.data.db.** { *; }
-keep class com.rapidraw.data.model.** { *; }
-dontwarn androidx.room.paging.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class com.rapidraw.data.model.** {
    *** Companion;
}
-keepclassmembers class com.rapidraw.data.model.Recipe$Companion { *; }
-keepclassmembers class com.rapidraw.data.model.Adjustments$Companion { *; }
-keepclassmembers class com.rapidraw.data.model.SidecarData$Companion { *; }

# Keep serializers for all model classes
-if class com.rapidraw.data.model.**
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if class com.rapidraw.data.model.**
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep enum classes and their values
-keepclassmembers enum com.rapidraw.data.model.** { *; }
-keepclassmembers enum com.rapidraw.core.** { *; }

# Keep Kotlin metadata
-keepattributes KotlinMetadata
-keep class kotlin.Metadata { *; }

# GPU/OpenGL
-keep class android.opengl.** { *; }

# Image decoding
-keep class android.graphics.ImageDecoder { *; }
-keep class android.graphics.BitmapFactory { *; }

# WorkManager
-keep class androidx.work.** { *; }
-keep class com.rapidraw.core.ExportWorker { *; }

# Keep Activity and Application classes
-keep public class com.rapidraw.MainActivity { *; }
-keep public class com.rapidraw.RapidRawApp { *; }

# Compose navigation & ViewModel factories
-keep class com.rapidraw.ui.** { *; }
-keepclassmembers class com.rapidraw.ui.** { *; }

# OpenGL Shaders
-keepclassmembers class com.rapidraw.core.GpuPipeline { *; }

# Core processing
-keep class com.rapidraw.core.** { *; }
-keepclassmembers class com.rapidraw.core.** { *; }

# Prevent stripping of line numbers for crash reports
-keepattributes SourceFile,LineNumberTable

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile *;
}

# Compose runtime
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-dontwarn androidx.compose.**

# Navigation
-keep class androidx.navigation.** { *; }

# Kotlin serialization — ensure all @Serializable classes are kept
-keep @kotlinx.serialization.Serializable class * { *; }

# ViewModel
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.ViewModelProvider$Factory { *; }

# Navigation routes / args classes
-keep class com.rapidraw.ui.navigation.** { *; }

# Keep all Composable functions
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# AutoService annotation processor references (not used at runtime)
-dontwarn javax.annotation.processing.AbstractProcessor
-dontwarn javax.annotation.processing.SupportedOptions

# v1.5.3 新增：JNI / Native 桥接
-keep class com.rapidraw.core.RawDecoder { *; }
-keep class com.rapidraw.core.RawDecoder$* { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# v1.5.3 新增：AndroidX AppCompat / LocaleManager
-keep class androidx.appcompat.app.** { *; }
-keep class androidx.core.os.LocaleListCompat { *; }
-keep class androidx.core.os.LocaleListCompat$* { *; }

# v1.5.3 新增：CrashHandler 反射访问 BuildConfig 与 R.font
-keep class com.rapidraw.BuildConfig { *; }
-keepclassmembers class com.rapidraw.R$font {
    public static <fields>;
}

# v1.5.3 新增：协程 / Flow 反射相关
-dontwarn kotlinx.coroutines.flow.**
-dontwarn java.lang.invoke.StringConcatFactory

# v1.5.3 新增：Material 3 与 Material Icons Extended（部分图标在 R8 后会被错误剥离）
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material.icons.** { *; }

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.gpu.GpuDelegateFactory$Options
-dontwarn org.tensorflow.lite.gpu.GpuDelegateFactory$Options$GpuBackend

# v1.5.9 hotfix: 保留 raw 资源，避免资源收缩误删 GPU shader 等运行时读取的文件。
-keepresources raw/**

# v1.5.9 hotfix: 禁用 R8 对 Log 调用的移除。
# 此前规则会删除 Log.v/d/i 调用，虽然编译期不会报错，但可能意外清空某些
# 异常路径的 catch 块，导致 release 包行为与 debug 包不一致，甚至掩盖崩溃根因。
# 保留完整日志调用，便于线上通过 crash log 诊断问题。
# -assumenosideeffects class android.util.Log { ... }

# 隐藏原始源文件名称
-renamesourcefileattribute SourceFile

# v1.6.1 安全加固: Release 构建中移除 Log.v/d/i 调用，防止敏感信息泄漏
# 仅保留 Log.w/e 用于 crash 诊断（wtf 保留用于 assert 级别问题）
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# v1.6.1 安全加固: 保留 Oklab/FilmPresets 等 v1.6.0 新增的序列化模型
-keep class com.rapidraw.data.model.FilmPresets { *; }
-keep class com.rapidraw.data.model.FilmPresets$* { *; }

# v1.6.1 安全加固: cloud sync 后端
-keep class com.rapidraw.cloud.** { *; }

# v1.6.2 兼容性加固: Android 16 StrictMode 内部类 (反射访问)
-keep class android.os.StrictMode$** { *; }
-dontwarn android.os.StrictMode$VmPolicy$Builder

# v1.6.2 兼容性加固: Android 16 predictive back (OnBackInvokedCallback)
-keep class androidx.activity.OnBackPressedDispatcher { *; }
-keep class androidx.activity.OnBackPressedDispatcher$** { *; }
-dontwarn androidx.activity.**

# v1.6.2 兼容性加固: Compose 交互源 (PressFeedback Modifier)
-keep class androidx.compose.foundation.interaction.** { *; }
-dontwarn androidx.compose.foundation.interaction.**

# v1.6.2 兼容性加固: 保持 Motion / PressFeedback 反射访问
-keep class com.rapidraw.ui.theme.Motion { *; }
-keep class com.rapidraw.ui.theme.Motion$** { *; }
-keep class com.rapidraw.ui.theme.PressFeedback { *; }
-keep class com.rapidraw.ui.theme.PressFeedback$** { *; }

# v1.6.2 兼容性加固: 保持 EditorViewModel 中 ViewModel 工厂
-keep class com.rapidraw.ui.editor.EditorViewModel$Factory { *; }
-keep class com.rapidraw.ui.editor.EditorViewModel$** { *; }

# v1.6.2 兼容性加固: 保持 CrashHandler 协程异常处理器
-keep class com.rapidraw.core.CrashHandler { *; }
-keep class com.rapidraw.core.CrashHandler$** { *; }

# v1.6.3 最严深度自检: 保持 R 类所有资源字段（反射访问 R$font / R$drawable）
-keep class com.rapidraw.R { *; }
-keep class com.rapidraw.R$* {
    public static <fields>;
}

# v1.6.3 最严深度自检: 保持 Type.kt 反射访问的 R$font
-keep class com.rapidraw.R$font {
    public static <fields>;
}

# v1.6.3 最严深度自检: 保持 HeifExporter / AvifExporter 反射类
-keep class android.heif.writer.HeifWriter { *; }
-keep class android.heif.writer.HeifWriter$Builder { *; }
-dontwarn android.heif.writer.**

# v1.6.3 最严深度自检: 保持 SystemProperties 反射（DeviceOptimizer / ColorScience）
-keep class android.os.SystemProperties { *; }
-dontwarn android.os.SystemProperties

# v1.6.3 最严深度自检: 保持 InferenceEngine 反射的 delegate close 方法
-keep class * {
    public void close();
}

# v1.6.3 最严深度自检: 保持 Bitmap.CompressFormat.AVIF 字段反射
-keep class android.graphics.Bitmap$CompressFormat {
    public static <fields>;
}

# v1.6.3 最严深度自检: 保持 AI 模块所有 native 方法和反射
-keep class com.rapidraw.ai.** { *; }
-keepclassmembers class com.rapidraw.ai.** {
    native <methods>;
}

# v1.6.3 最严深度自检: 保持 cloud sync 后端所有类
-keep class com.rapidraw.cloud.** { *; }

# v1.6.3 最严深度自检: 保持 Sidecar / Merkle 哈希序列化
-keep class com.rapidraw.core.SidecarManager { *; }
-keep class com.rapidraw.core.BranchableHistory { *; }
-keep class com.rapidraw.core.BranchableHistory$** { *; }

# v1.6.3 最严深度自检: 保持 FilmPresets 所有数据类
-keep class com.rapidraw.data.model.FilmPresets { *; }
-keep class com.rapidraw.data.model.FilmPresets$** { *; }

# v1.6.3 最严深度自检: 保持 ExportQueueProcessor / ExportWorker
-keep class com.rapidraw.data.export.** { *; }
-keep class com.rapidraw.data.repository.** { *; }

# v1.10.0 安全加固: 保持安全模块所有类
-keep class com.rapidraw.security.** { *; }
-keepclassmembers class com.rapidraw.security.** { *; }

# v1.10.0 无障碍: 保持无障碍辅助类
-keep class com.rapidraw.ui.accessibility.** { *; }
-keepclassmembers class com.rapidraw.ui.accessibility.** { *; }

# v1.10.0 DI 容器: 保持 DiContainer 反射访问
-keep class com.rapidraw.core.DiContainer { *; }
-keep class com.rapidraw.core.DiContainer$** { *; }

# v1.10.0 安全: 保持 Android Keystore 依赖
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }
-dontwarn javax.crypto.**
-dontwarn java.security.spec.**

# v1.10.2 兼容性: 保持兼容性模块
-keep class com.rapidraw.core.BackgroundCompatibility { *; }
-keep class com.rapidraw.core.OemCompatibility { *; }
-keep class com.rapidraw.core.SystemCompatibility { *; }
-keep class com.rapidraw.core.PlayIntegrityHelper { *; }
-keepclassmembers class com.rapidraw.core.SystemCompatibility$CompatibilityReport { *; }

# v1.10.3 功能可用性: 保持通知渠道和应用内评价
-keep class com.rapidraw.core.NotificationChannels { *; }
-keep class com.rapidraw.core.InAppReviewManager { *; }
-keep class com.rapidraw.ui.components.WhatsNewDialog { *; }
-keep class com.rapidraw.ui.components.WhatsNewDialogKt { *; }
