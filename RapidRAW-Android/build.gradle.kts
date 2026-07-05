// 2026 toolchain: Android Gradle Plugin 8.6 + Kotlin 2.0
// 用于 Android 16 (API 36) 兼容与 16KB page size 支持
// 注意：AGP 8.6+ 已支持 16KB page size；Kotlin 2.0+ 启用 K2 编译器
// v1.10.1: 使用 Gradle Version Catalog 统一管理依赖版本
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover) apply false
}
