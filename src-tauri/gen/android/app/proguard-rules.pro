# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep rustls-platform-verifier JNI classes
-keep, includedescriptorclasses class org.rustls.platformverifier.** { *; }

# Keep Tauri JNI classes
-keep class app.tauri.** { *; }
-keep class **.TauriActivity { *; }

# Keep RapidRAW MainActivity
-keep class io.github.CyberTimon.RapidRAW.MainActivity { *; }

# Keep all Kotlin metadata
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations

# Keep all native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep WebView JavaScript interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep all exceptions
-keep public class * extends java.lang.Exception

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# Keep all classes referenced from JNI
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep AndroidX WebKit classes
-keep class androidx.webkit.** { *; }

# Keep AppCompat classes
-keep class androidx.appcompat.** { *; }

# Keep Material Design classes
-keep class com.google.android.material.** { *; }

# Keep Activity Result API classes (for image picker)
-keep class androidx.activity.result.** { *; }