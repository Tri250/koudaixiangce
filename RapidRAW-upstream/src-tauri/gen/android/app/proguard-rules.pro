# RapidRAW ProGuard/R8 Rules - Release Production Configuration
# Target: Tauri 2 Android WebView Application

# ============================================
# General Optimization Settings
# ============================================
-dontwarn org.w3c.dom.**
-dontwarn javax.xml.**
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*
-keepattributes Exceptions,InnerClasses,Signature,EnclosingMethod

# ============================================
# WebView & JavaScript Interface Protection
# ============================================
-keepclassmembers class io.github.CyberTimon.RapidRAW.** {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * {
    @com.google.android.apps.internal.androidtestapi.PublicApi <methods>;
}

# ============================================
# Kotlin / Coroutines Preservation
# ============================================
-keepnames class kotlinx.** { *; }
-dontnote kotlinx.**
-keepclassmembers class kotlinx.coroutines.internal.MainDispatcherFactory {
    *** getSupport*(...);
}
-keepclassmembers class kotlin.coroutines.jvm.internal.ContinuationImpl {
    <init>(...);
}

# ============================================
# AndroidX / Jetpack Libraries Preservation
# ============================================
-keep class android.webkit.** { *; }
-dontwarn android.webkit.**
-keep class androidx.appcompat.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.core.** { *; }
-dontwarn androidx.**

# ============================================
# Material Design Components
# ============================================
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ============================================
# Tauri Core Preservation (Critical)
# ============================================
-keep class tauri.** { *; }
-keep class io.tauri.** { *; }
-keep class app.tauri.** { *; }
-keep class dev.kraken.** { *; }

# Preserve Tauri IPC channel classes
-keepclasseswithmembers class * {
    @tauri.Invoke <methods>;
}

# ============================================
# Native Library (.so) Loading Preservation
# ===========================================
-keepclasseswithmembernames class * {
    native <methods>;
}

# Prevent removal of JNI-related classes
-keep class **JNI* { *; }
-keep class **Jni* { *; }
-keep class **Native* { *; }

# ============================================
# Serialization / Data Model Preservation
# ============================================
-keepattributes Signature
-keep class **$$serializer { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================
# Logging & Debug Removal in Release
# ============================================
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(...);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}
-assumenosideeffects class java.lang.System {
    public static long currentTimeMillis();
}

# ============================================
# Code Shrinking Safety
# ============================================
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Fragment

# ============================================
# Obfuscation Exclusions for Reflection Usage
# ============================================
-keep class io.github.CyberTimon.RapidRAW.MainActivity { *; }
-keep class io.github.CyberTimon.RapidRAW.SplashActivity { *; }
-keep class **$Creator { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
