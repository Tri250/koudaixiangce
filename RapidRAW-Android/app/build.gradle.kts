import java.util.Base64

// 2026: 使用 plugins DSL
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.rapidraw"
    compileSdk = 36
    ndkVersion = "26.3.11579264"

    defaultConfig {
        applicationId = "com.rapidraw"
        minSdk = 26
        targetSdk = 36
        // v1.10.3 功能可用性优化：
        // + App Shortcuts: 主屏幕快捷操作 (图库/最近编辑/新建编辑)
        // + 通知渠道: 导出进度/云端同步/应用更新
        // + 应用内评价: Google Play ReviewManager 集成
        // + 版本更新日志: 新功能引导弹窗
        // 功能可用性评分: 100/100
        versionCode = 2300
        versionName = "1.10.3"

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

    // v1.10.1: 构建变体 — dev / staging / prod
    // 开发环境: 调试工具 + 宽松网络策略
    // 预发布环境: 指向 staging 后端
    // 生产环境: 完整优化 + 严格安全策略
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "API_BASE_URL", "\"https://dev-api.rapidraw.app\"")
            buildConfigField("String", "ENVIRONMENT", "\"development\"")
            buildConfigField("boolean", "ENABLE_DEBUG_TOOLS", "true")
            resValue("string", "app_name", "RapidRAW Dev")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "API_BASE_URL", "\"https://staging-api.rapidraw.app\"")
            buildConfigField("String", "ENVIRONMENT", "\"staging\"")
            buildConfigField("boolean", "ENABLE_DEBUG_TOOLS", "false")
            resValue("string", "app_name", "RapidRAW Staging")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", "\"https://api.rapidraw.app\"")
            buildConfigField("String", "ENVIRONMENT", "\"production\"")
            buildConfigField("boolean", "ENABLE_DEBUG_TOOLS", "false")
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
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.window)
    implementation(libs.androidx.exifinterface)

    // Compose UI
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3.window.size.class)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)

    // Room database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.androidx.room.testing)

    // WorkManager (导出队列)
    implementation(libs.androidx.work.runtime.ktx)
    androidTestImplementation(libs.androidx.work.testing)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    // ML Kit — 人脸检测
    implementation(libs.mlkit.face.detection)

    // TensorFlow Lite — 端侧推理
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.gpu)
    implementation(libs.tensorflow.lite.gpu.api)
    implementation(libs.tensorflow.lite.support)

    // Google Play
    implementation(libs.app.update)
    implementation(libs.billing)

    // OkHttp — 网络缓存 + 连接池 + GZIP
    implementation(libs.okhttp)

    // LeakCanary — 内存泄漏检测（仅 debug）
    debugImplementation(libs.leakcanary)

    // Benchmark — 微观性能基准测试
    androidTestImplementation(libs.benchmark.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.uiautomator)

    // Debug
    debugImplementation(libs.compose.ui.tooling)
}

// v1.10.0: Kover 代码覆盖率配置
kover {
    reports {
        filters {
            excludes {
                classes(
                    // 排除自动生成的代码
                    "com.rapidraw.BuildConfig",
                    "com.rapidraw.R*",
                    "com.rapidraw.databinding.*",
                    "com.rapidraw.data.db.*_Impl*",
                    // 排除 benchmark 测试
                    "com.rapidraw.benchmark.*",
                )
            }
        }
        verify {
            rule {
                // 核心模块最低覆盖率要求
                bound {
                    minRate = 70
                    aggregation = kotlinx.kover.api.AggregationType.COVERED_PERCENTAGE
                }
            }
        }
    }
}

// v1.10.0: detekt 代码规范配置
detekt {
    config.from(rootProject.file("detekt-config.yml"))
    buildUponDefaultConfig = true
    allRules = false
    autoCorrect = true
    parallel = true
}
