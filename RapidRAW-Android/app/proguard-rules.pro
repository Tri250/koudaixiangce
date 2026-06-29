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

# Keep all Composable functions
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# AutoService annotation processor references (not used at runtime)
-dontwarn javax.annotation.processing.AbstractProcessor
-dontwarn javax.annotation.processing.SupportedOptions
