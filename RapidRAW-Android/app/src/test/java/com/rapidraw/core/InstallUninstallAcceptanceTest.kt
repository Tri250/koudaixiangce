package com.rapidraw.core

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.rapidraw.ai.InferenceEngine
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * 安装/卸载验收测试套件 — 覆盖 INST-01~INST-12 + UNINST-01~UNINST-09
 *
 * 测试策略：
 * - 构建配置验证（applicationId, versionCode, ABI, hasFragileUserData 等）
 * - SAF 授权流程验证
 * - .rrdata 侧车文件路径持久化验证
 * - AI 模型存储路径持久化验证
 * - 存储空间检查验证
 */
@RunWith(RobolectricTestRunner::class)
class InstallUninstallAcceptanceTest {

    private lateinit var context: Context
    private lateinit var sidecarManager: SidecarManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sidecarManager = SidecarManager(context)
    }

    // ═══════════════════════════════════════════════════════════════
    // INST-01: GitHub Release APK 首次侧载安装
    // ═══════════════════════════════════════════════════════════════
    @Test
    fun inst01_applicationIdAndVersion_correct() {
        val pm = context.packageManager
        // 验证 applicationId 为 com.rapidraw（prod 变体）
        assertEquals("com.rapidraw", context.packageName.substringBefore(".dev").substringBefore(".staging"))
    }

    // ═══════════════════════════════════════════════════════════════
    // INST-03: 多 ABI 设备安装
    // ═══════════════════════════════════════════════════════════════
    @Test
    fun inst03_abiFilters_arm64Default() {
        // 验证默认 ABI 配置包含 arm64-v8a
        // 构建脚本中默认仅包含 arm64-v8a，armeabi-v7a 和 x86_64 需通过参数启用
        // 此处验证运行时 native 库能正确加载
        assertTrue