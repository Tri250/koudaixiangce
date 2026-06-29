// 2026: 使用 plugins DSL
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.rapidraw"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rapidraw"
        minSdk = 26
        targetSdk = 36
        versionCode = 150
        versionName = "1.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Android 15+ (API 35) 16KB page size compatibility:
        // opt into native libraries aligned to 16KB page boundaries.
        // Required by Google Play for all apps targeting API 35+ from Nov 1, 2025.
        // https://developer.android.com/guide/practices/page-sizes
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // 16KB page size: pack native libraries with 16KB alignment for Android 15+ devices
    // that use 16KB pages (e.g. Pixel 8+ on Android 16, OPPO Find X8 series with ColorOS 16).
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // AGP 8.5+ supports 16KB page alignment for unpacked native libraries.
            useLegacyPackaging = false
        }
    }

    // 仅在存在 release.keystore 时创建 release 签名配置，避免 debug/日常构建因缺少环境变量而失败
    val releaseKeystore = rootProject.file("app/release.keystore")
    val hasReleaseKeystore = releaseKeystore.exists()
    if (hasReleaseKeystore) {
        signingConfigs {
            create("release") {
                storeFile = releaseKeystore
                // 禁止硬编码回退密码；CI 或发布构建前必须通过环境变量注入
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: throw GradleException("Missing environment variable KEYSTORE_PASSWORD for release signing")
                keyAlias = "rapidraw"
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: throw GradleException("Missing environment variable KEY_PASSWORD for release signing")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            // 启用测试覆盖率仅在 debug，release 保持关闭避免性能损耗
            isTestCoverageEnabled = false
        }
        debug {
            isMinifyEnabled = false
            isTestCoverageEnabled = true
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
                it.systemProperty("robolectric.invokedynamic.enable", "false")
            }
        }
        animationsDisabled = true
    }

    lint {
        disable += listOf("MissingTranslation", "UnusedResources")
        abortOnError = false
        checkReleaseBuilds = false
        checkAllWarnings = true
        warningsAsErrors = false
        xmlReport = true
        htmlReport = true
    }

}

dependencies {
    // Compose BOM (2026 release track - supports Material 3 Expressive + new APIs)
    val composeBom = platform("androidx.compose:compose-bom:2025.04.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // Android 13+ per-app language (locale preference)
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Window manager for foldables / multi-window on Android 16
    implementation("androidx.window:window:1.3.0")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Image decoding
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    // Room database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    testImplementation("androidx.room:room-testing:$roomVersion")

    // WorkManager (导出队列)
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    androidTestImplementation("androidx.work:work-testing:2.10.0")

    // Crash reporting
    implementation("ch.acra:acra-core:5.11.3")
    implementation("ch.acra:acra-http:5.11.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("app.cash.turbine:turbine:1.1.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
}
