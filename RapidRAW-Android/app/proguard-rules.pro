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

# Compose
-keep class androidx.compose.** { *; }

# OpenGL Shaders
-keepclassmembers class com.rapidraw.core.GpuPipeline { *; }

# AiInpainter / AiDenoiser / AiMaskGenerator
-keepclassmembers class com.rapidraw.core.AiInpainter { *; }
-keepclassmembers class com.rapidraw.core.AiDenoiser { *; }
-keepclassmembers class com.rapidraw.core.AiMaskGenerator { *; }

# FlowMaskManager
-keepclassmembers class com.rapidraw.core.FlowMaskManager { *; }

# SidecarManager
-keepclassmembers class com.rapidraw.core.SidecarManager { *; }

# CubeLutParser / LutManager
-keepclassmembers class com.rapidraw.core.CubeLutParser { *; }
-keep class com.rapidraw.core.CubeLutParser$Lut3D { *; }
-keepclassmembers class com.rapidraw.core.LutManager { *; }

# FilmGrainRenderer
-keepclassmembers class com.rapidraw.core.FilmGrainRenderer { *; }

# HighlightReconstructor
-keepclassmembers class com.rapidraw.core.HighlightReconstructor { *; }

# LensCorrector / PerspectiveCorrector
-keepclassmembers class com.rapidraw.core.LensCorrector { *; }
-keepclassmembers class com.rapidraw.core.PerspectiveCorrector { *; }

# ColorMath
-keepclassmembers class com.rapidraw.core.ColorMath { *; }

# SmartOptimizer / SceneClassifier
-keepclassmembers class com.rapidraw.core.SmartOptimizer { *; }
-keepclassmembers class com.rapidraw.core.SceneClassifier { *; }

# UserPreferenceLearning
-keepclassmembers class com.rapidraw.core.UserPreferenceLearning { *; }
