// PixelFruit-ref Android Library Module
// 对标 PixelFruit v1.2.2：RAW 解析 + 调色滤镜 + LUT 引擎
// P-01~P-09 用例覆盖

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pixelfruit"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
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
    // Coroutines for native thread offloading (P-09)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // ExifInterface for RAW metadata (P-01)
    implementation("androidx.exifinterface:exifinterface:1.4.1")

    // RapidRAW Android core (shared RAW decoder)
    compileOnly(project(":RapidRAW-Android"))
}