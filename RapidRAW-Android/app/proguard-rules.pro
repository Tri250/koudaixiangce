# RapidRAW ProGuard Rules — v1.5.5 精细化优化
# 原则：仅保留运行时必要的最小 keep 范围，让 R8 最大化代码收缩和优化

# ===== Room =====
-keep class androidx.room.** { *; }
-keep class com.rapidraw.data.db.** { *; }
-dontwarn androidx.room.paging.**

# ===== kotlinx.serialization =====
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class com.rapidraw.data.model.** {
    *** Companion;
}
-keepclassmembers class com.rapidraw.data.model.Recipe$Companion { *; }
-keepclassmembers class com.rapidraw.data.model.Adjustments$Companion { *; }
-keepclassmembers class com.rapidraw.data.model.SidecarData$Companion { *; }

# Keep serializers for all @Serializable model classes
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

# ===== kotlinx.serialization — all @Serializable classes =====
-keep @kotlinx.serialization.Serializable class * { *; }

# ===== GPU / OpenGL =====
-keep class android.opengl.** { *; }

# ===== Image decoding =====
-keep class android.graphics.ImageDecoder { *; }
-keep class android.graphics.BitmapFactory { *; }

# ===== WorkManager =====
# 仅保留 WorkManager 核心和我们自定义的 Worker 类，不过度保留整个包
-keep class androidx.work.WorkManager { *; }
-keep class androidx.work.WorkerParameters { *; }
-keep class androidx.work.Data { *; }
-keep class androidx.work.ListenableWorker { *; }
-keep class androidx.work.CoroutineWorker { *; }
-keep class com.rapidraw.core.ExportWorker { *; }
-keepclassmembers class com.rapidraw.core.ExportWorker { *; }
-dontwarn androidx.work.**

# ===== Activity and Application =====
-keep public class com.rapidraw.MainActivity { *; }
-keep public class com.rapidraw.RapidRawApp { *; }

# ===== Compose navigation & ViewModel =====
# 仅保留导航路由类和 ViewModel，不保留整个 ui 包
-keep class com.rapidraw.ui.navigation.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.ViewModelProvider$Factory { *; }

# ===== OpenGL Shaders =====
-keepclassmembers class com.rapidraw.core.GpuPipeline { *; }

# ===== Core processing — 精细化：仅保留 JNI 桥接和反射访问的类 =====
-keep class com.rapidraw.core.RawDecoder { *; }
-keep class com.rapidraw.core.RawDecoder$* { *; }
-keep class com.rapidraw.core.ImageProcessor { *; }
-keepclassmembers class com.rapidraw.core.ImageProcessor { *; }
-keep class com.rapidraw.core.CrashHandler { *; }
-keep class com.rapidraw.core.SidecarManager { *; }
-keep class com.rapidraw.core.ExportQueueProcessor { *; }
-keep class com.rapidraw.core.GpuPipeline { *; }
# AI 推理相关
-keep class com.rapidraw.ai.InferenceEngine { *; }
-keep class com.rapidraw.ai.ModelManager { *; }
-keepclassmembers class com.rapidraw.ai.InferenceEngine { *; }
-keepclassmembers class com.rapidraw.ai.ModelManager { *; }
# JNI native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# ===== Prevent stripping of line numbers for crash reports =====
-keepattributes SourceFile,LineNumberTable

# ===== Kotlin coroutines =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile *;
}

# ===== Compose runtime — 精细化：仅保留必要的运行时类 =====
-keep class androidx.compose.runtime.Composer { *; }
-keep class androidx.compose.runtime.Composition { *; }
-keep class androidx.compose.runtime.Recomposer { *; }
-dontwarn androidx.compose.**

# ===== Compose UI — 仅保留必要的框架类 =====
-keep class androidx.compose.ui.node.LayoutNode { *; }
-dontwarn androidx.compose.ui.**

# ===== Navigation =====
-keep class androidx.navigation.** { *; }

# ===== Keep all Composable functions =====
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ===== AutoService annotation processor references =====
-dontwarn javax.annotation.processing.AbstractProcessor
-dontwarn javax.annotation.processing.SupportedOptions

# ===== AndroidX AppCompat / LocaleManager =====
-keep class androidx.appcompat.app.** { *; }
-keep class androidx.core.os.LocaleListCompat { *; }
-keep class androidx.core.os.LocaleListCompat$* { *; }

# ===== CrashHandler 反射访问 BuildConfig 与 R.font =====
-keep class com.rapidraw.BuildConfig { *; }
-keepclassmembers class com.rapidraw.R$font {
    public static <fields>;
}

# ===== 协程 / Flow 反射相关 =====
-dontwarn kotlinx.coroutines.flow.**
-dontwarn java.lang.invoke.StringConcatFactory

# ===== Material 3 — 精细化：仅保留必要的组件类 =====
# Material3 组件通过 Composable 函数已被保留，无需保留整个包
# 以下仅保留可能被反射访问的类
-keep class androidx.compose.material3.MaterialTheme { *; }
-dontwarn androidx.compose.material3.**

# ===== Material Icons Extended =====
# Material Icons Extended 包含大量图标，R8 可能错误剥离
# 仅保留通过 @Composable 引用的图标（已由 Composable 规则覆盖）
-dontwarn androidx.compose.material.icons.**

# ===== TensorFlow Lite =====
-keep class org.tensorflow.lite.Interpreter { *; }
-keep class org.tensorflow.lite.Interpreter$Options { *; }
-keep class org.tensorflow.lite.DataType { *; }
-keep class org.tensorflow.lite.TensorImage { *; }
-keep class org.tensorflow.lite.TensorBuffer { *; }
-keep class org.tensorflow.lite.support.image.TensorImage { *; }
-keep class org.tensorflow.lite.support.tensorbuffer.TensorBuffer { *; }
-keepclassmembers class org.tensorflow.lite.Interpreter { *; }
-keepclassmembers class org.tensorflow.lite.Interpreter$Options { *; }
-dontwarn org.tensorflow.lite.gpu.GpuDelegateFactory$Options
-dontwarn org.tensorflow.lite.gpu.GpuDelegateFactory$Options$GpuBackend
-dontwarn org.tensorflow.lite.**

# ===== ML Kit Face Detection =====
-keep class com.google.mlkit.vision.face.** { *; }
-dontwarn com.google.mlkit.**

# ===== v1.5.5 安全加固：release 构建下移除低优先级 Log 调用 =====
# 保留 Log.w 和 Log.e，移除后会导致 catch 块变空操作，R8 优化掉 catch 块导致闪退
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ===== 隐藏原始源文件名称 =====
-renamesourcefileattribute SourceFile
