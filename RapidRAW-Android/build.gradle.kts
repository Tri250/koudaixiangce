// 2026 toolchain: Android Gradle Plugin 8.6 + Kotlin 2.0
// 用于 Android 16 (API 36) 兼容与 16KB page size 支持
// 注意：AGP 8.6+ 已支持 16KB page size；Kotlin 2.0+ 启用 K2 编译器
plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    // v1.10.0: 代码质量工具
    id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false
    id("org.jetbrains.kotlinx.kover") version "0.8.3" apply false
}
