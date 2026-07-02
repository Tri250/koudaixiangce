// AlcedoStudio Android Application Module
// 相册管理 + 模块调度 + 媒体库集成
// A-01~A-07 + X-02 用例覆盖

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alcedo.studio"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alcedo.studio"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // 跨模块依赖 — 三模块核心链路
    implementation(project(":RapidRAW-Android"))
    implementation(project(":PixelFruit-ref"))

    // Scoped Storage (A-05, X-04)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // ExifInterface
    implementation("androidx.exifinterface:exifinterface:1.4.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}