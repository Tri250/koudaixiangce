-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable
-keepattributes EnclosingMethod

-dontwarn java.lang.invoke.**

-keep class com.alcedo.studio.data.model.** { *; }

-keep class com.alcedo.studio.data.db.** { *; }
-keep class com.alcedo.studio.data.db.* { *; }
-keepclassmembers class com.alcedo.studio.data.db.* {
    @androidx.room.* *;
}

-keep class com.alcedo.studio.data.converter.** { *; }

-keep class com.alcedo.studio.data.repository.** { *; }

-keep class com.alcedo.studio.ui.viewmodel.** { *; }
-keepclassmembers class com.alcedo.studio.ui.viewmodel.** {
    @javax.inject.Inject <init>();
}

-keep class com.alcedo.studio.core.** { *; }

-keep class com.alcedo.studio.di.** { *; }

-keep class com.alcedo.studio.navigation.** { *; }

-keep class com.alcedo.studio.AlcedoStudioApp { *; }

-keepclassmembers class * extends androidx.appcompat.app.AppCompatActivity {
    public void *(android.view.View);
}

-keepclassmembers class * extends androidx.fragment.app.Fragment {
    public void *(android.view.View);
}

-keepnames class * implements kotlinx.serialization.KSerializer
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    static ** INSTANCE;
}

-keep class kotlinx.serialization.json.** { *; }
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
    *** INSTANCE;
}

-keep class androidx.compose.ui.** { *; }
-keepclassmembers class androidx.compose.ui.** {
    *** Companion;
    @androidx.compose.runtime.Composable ***;
}

-keep class androidx.compose.material3.** { *; }

-keep class androidx.lifecycle.** { *; }
-keepclassmembers class androidx.lifecycle.** {
    @androidx.lifecycle.OnLifecycleEvent *;
}

-keep class androidx.work.** { *; }
-keepclassmembers class androidx.work.** {
    @androidx.work.WorkerParameters *;
}

-keep class androidx.room.** { *; }
-keepclassmembers class androidx.room.** {
    *** Companion;
}

-keep class androidx.exifinterface.media.ExifInterface { *; }

-keep class io.coil.** { *; }

-keep class com.google.dagger.** { *; }
-keep class javax.inject.** { *; }

-repackageclasses 'com.alcedo.studio.internal'
-allowaccessmodification
-optimizations !code/allocation/variable

-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

-assumenosideeffects class com.alcedo.studio.core.L {
    public static void d(...);
    public static void i(...);
    public static void w(...);
    public static void e(...);
}
