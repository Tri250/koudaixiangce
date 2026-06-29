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
