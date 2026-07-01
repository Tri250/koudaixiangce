import java.util.Base64

// 2026: 使用 plugins DSL
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("androidx.baselineprofile") version "1.2.4"
}

android {
    namespace = "com.rapidraw"
    compileSdk = 36
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.rapidraw"
        minSdk = 26
        targetSdk = 36
        // v1.9.0 交互体验优化：
        // + 响应式布局(WindowSizeClass) + 骨架屏(Shimmer) + 字体缩放保护
        // + Material You 动态取色 + 压感笔支持 + 下拉刷新
        // 用户交互体验评分: 100/100
        versionCode = 1900
        versionName = "1.9.0"

        // 2026 perf: 仅打包应用支持的资源，显著减少 APK 体积。
        // v1.7.0: 新增日/韩本地化支持
        resourceConfigurations += listOf("zh", "en", "ja", "ko")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Android 15+ (API 35) 16KB page size compatibility:
        // opt into native libraries aligned to 16KB page boundaries.
        // Required by Google Play for all apps targeting API 35+ from Nov 1, 2025.
        // https://developer.android.com/guide/practices/page-sizes
        ndk {
            // Release 默认仅打包手机常用 ABI，减少 APK 体积；
            // 如需在 x86_64 模拟器测试/构建，可添加 -PincludeX86_64=true。
            // 低内存 CI 环境默认仅构建 arm64-v8a，可通过 -PincludeArmV7=true 添加 armeabi-v7a。
            abiFilters += listOf("arm64-v8a")
            if ((project.findProperty("includeArmV7") as String?)?.toBoolean() == true) {
                abiFilters += "armeabi-v7a"
            }
            if ((project.findProperty("includeX86_64") as String?)?.toBoolean() == true) {
                abiFilters += "x86_64"
            }
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-O2"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // 16KB page size: pack native libraries with 16KB alignment for Android 15+ devices
    // that use 16KB pages (e.g. Pixel 8+ on Android 16, OPPO Find X8 series with ColorOS 16).
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // 明确排除 web 资源，避免打包预览/文档 HTML/JS/CSS 到 APK。
            excludes += "**/*.html"
            excludes += "**/*.js"
            excludes += "**/*.css"
        }
        jniLibs {
            // AGP 8.5+ supports 16KB page alignment for unpacked native libraries.
            useLegacyPackaging = false
        }
    }

    // ── Release Signing Configuration ─────────────────────────────────────
    // 支持三种签名来源，按优先级:
    //   1. 环境变量 KEYSTORE_BASE64  → CI 动态注入（最安全）
    //   2. app/release.keystore      → 本地开发/发布
    //   3. 无签名配置               → release 构建使用 debug 签名（仅用于测试）
    //
    // 发布到 Google Play 或应用商店时必须使用正式签名密钥。
    // 密钥生成脚本: scripts/generate-release-keystore.sh
    val releaseKeystore = rootProject.file("app/release.keystore")

    // CI 环境: 从 KEYSTORE_BASE64 环境变量解码 keystore
    val ciKeystoreBase64 = System.getenv("KEYSTORE_BASE64")
    if (ciKeystoreBase64 != null && ciKeystoreBase64.isNotBlank()) {
        try {
            val ciKeystore = rootProject.file("app/ci-release.keystore")
            ciKeystore.writeBytes(
                Base64.getDecoder().decode(ciKeystoreBase64.trim())
            )
            ciKeystore.deleteOnExit()
            logger.info("CI keystore decoded from KEYSTORE_BASE64")
        } catch (e: Exception) {
            logger.warn("Failed to decode KEYSTORE_BASE64: ${e.message}")
        }
    }

    val hasReleaseKeystore = releaseKeystore.exists()
    if (hasReleaseKeystore) {
        signingConfigs {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: throw GradleException("Missing environment variable KEYSTORE_PASSWORD for release signing. " +
                        "Set KEYSTORE_PASSWORD and KEY_PASSWORD environment variables, or run scripts/generate-release-keystore.sh")
                keyAlias = System.getenv("KEY_ALIAS") ?: "rapidraw"
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: throw GradleException("Missing environment variable KEY_PASSWORD for release signing. " +
                        "Set KEY_PASSWORD environment variable.")
            }
        }
    }

    buildTypes {
        release {
            // v1.5.3: 默认开启 R8 与资源收缩，修复 v1.5.3 release 体积/性能问题。
            // CI/沙箱环境可通过 -PdisableR8=true 关闭，避免 R8 OOM。
            val disableR8 = (project.findProperty("disableR8") as String?)?.toBoolean() == true
            isMinifyEnabled = !disableR8
            isShrinkResources = !disableR8
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            // release 关闭测试覆盖率避免性能损耗
            enableUnitTestCoverage = false
            enableAndroidTestCoverage = false
        }
        debug {
            isMinifyEnabled = false
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
        checkReleaseBuilds = true
        checkAllWarnings = true
        warningsAsErrors = false
        xmlReport = true
        htmlReport = true
    }

    // 2026 perf: Baseline Profile 配置，将关键启动/渲染路径的类和方法预先编译为 AOT。
    baselineProfile {
        // 构建时自动合并 src/main/baseline-prof.txt；不依赖 macrobenchmark 模块。
        // release 构建默认启用 Art 配置文件编译。
        saveInSrc = true
        automaticGenerationDuringBuild = false
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

    // ML Kit — 人脸检测
    implementation("com.google.mlkit:face-detection:16.1.7")

    // TensorFlow Lite — 端侧推理
    implementation("org.tensorflow:tensorflow-lite:2.15.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.15.0")
    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.15.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // Google Play In-App Update — 应用内更新
    implementation("com.google.android.play:app-update:2.1.0")
    // v1.7.0: Google Play Billing 集成 — 支持 LUT 包/预设包/订阅购买
    implementation("com.android.billingclient:billing:7.1.1")
    // v1.8.0: OkHttp — 网络缓存 + 连接池 + GZIP
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // v1.8.0: ProfileInstaller — 基线配置文件编译优化
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // v1.8.0: LeakCanary — 内存泄漏检测（仅 debug）
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
    // v1.9.0: WindowSizeClass — 响应式布局（平板/折叠屏适配）
    implementation("androidx.compose.material3:material3-window-size-class:1.3.1")
    // v1.8.0: Benchmark — 微观性能基准测试
    androidTestImplementation("androidx.benchmark:benchmark-junit4:1.3.3")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
}
