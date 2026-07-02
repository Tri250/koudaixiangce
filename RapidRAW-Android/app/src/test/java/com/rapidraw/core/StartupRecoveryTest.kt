package com.rapidraw.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * StartupRecovery 单元测试。
 *
 * 覆盖 v2026.07 重构后的启动崩溃恢复逻辑：
 * 1. 连续启动计数器从 0 递增
 * 2. 达到阈值（>=3）时触发恢复
 * 3. 触发恢复后，cacheDir 被清空
 * 4. 触发恢复后，损坏的 SharedPreferences 被清理，**白名单保留**
 * 5. 启动成功后计数器重置
 * 6. 恢复流程本身失败不抛异常（runCatching 兜底）
 *
 * @since 2026.07
 */
@RunWith(RobolectricTestRunner::class)
class StartupRecoveryTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // 清空恢复相关的 prefs 与文件
        ctx.getSharedPreferences(StartupRecovery.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        ctx.cacheDir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
        val prefsDir = prefsDir()
        prefsDir?.listFiles()?.forEach { it.delete() }
    }

    @After
    fun tearDown() {
        ctx.getSharedPreferences(StartupRecovery.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        ctx.cacheDir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
        prefsDir()?.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun `fresh install - first startup - count becomes 1, no recovery`() {
        val decision = StartupRecovery.onStartupBegin(ctx)
        assertFalse("First startup should NOT trigger recovery", decision.shouldRecover)
        assertEquals(1, decision.count)
    }

    @Test
    fun `two consecutive starts without success - count is 2, no recovery`() {
        StartupRecovery.onStartupBegin(ctx)
        val decision = StartupRecovery.onStartupBegin(ctx)
        assertFalse(decision.shouldRecover)
        assertEquals(2, decision.count)
    }

    @Test
    fun `third start without success - triggers recovery and resets count`() {
        StartupRecovery.onStartupBegin(ctx)
        StartupRecovery.onStartupBegin(ctx)
        val decision = StartupRecovery.onStartupBegin(ctx)
        assertTrue("Third start should trigger recovery", decision.shouldRecover)
        // 恢复后计数器应被重置为 0
        val prefs = SafePreferences.get(ctx, StartupRecovery.PREFS_NAME)
        assertEquals(0, SafePreferences.getInt(prefs, StartupRecovery.KEY_CRASH_COUNT, -1))
    }

    @Test
    fun `startup success - resets counter to 0`() {
        StartupRecovery.onStartupBegin(ctx)
        StartupRecovery.onStartupBegin(ctx)
        StartupRecovery.onStartupSuccess(ctx)
        val prefs = SafePreferences.get(ctx, StartupRecovery.PREFS_NAME)
        assertEquals(0, SafePreferences.getInt(prefs, StartupRecovery.KEY_CRASH_COUNT, -1))
    }

    @Test
    fun `performRecovery - clears cache dir`() {
        // 在 cacheDir 放一个文件
        val cacheFile = File(ctx.cacheDir, "test_crash_recovery.tmp").apply {
            writeText("damaged")
        }
        assertTrue(cacheFile.exists())
        StartupRecovery.performRecovery(ctx)
        assertFalse("Cache file should be deleted", cacheFile.exists())
    }

    @Test
    fun `performRecovery - removes corrupted non-preserved prefs and keeps preserved ones`() {
        val prefsDir = prefsDir()!!

        val preservedOnboarding = File(prefsDir, "rapidraw_onboarding.xml").apply {
            writeText("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map/>")
        }
        val preservedPermissionHistory = File(prefsDir, "permission_history.xml").apply {
            writeText("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map/>")
        }
        val preservedPendingUri = File(prefsDir, "rapidraw_pending_uri.xml").apply {
            writeText("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map/>")
        }
        val preservedStartup = File(prefsDir, "${StartupRecovery.PREFS_NAME}.xml").apply {
            writeText("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map/>")
        }
        val corruptedOld = File(prefsDir, "rapidraw_legacy_corrupted.xml").apply {
            writeText("### corrupted ###")
        }
        val corruptedBak = File(prefsDir, "rapidraw_legacy_corrupted.xml.bak").apply {
            writeText("### corrupted bak ###")
        }

        StartupRecovery.performRecovery(ctx)

        assertTrue("rapidraw_onboarding.xml preserved", preservedOnboarding.exists())
        assertTrue("permission_history.xml preserved", preservedPermissionHistory.exists())
        assertTrue("rapidraw_pending_uri.xml preserved", preservedPendingUri.exists())
        assertTrue("rapidraw_startup.xml preserved", preservedStartup.exists())
        assertFalse("Corrupted legacy prefs removed", corruptedOld.exists())
        assertFalse("Corrupted legacy .bak removed", corruptedBak.exists())
    }

    @Test
    fun `performRecovery - does not throw when prefs dir does not exist`() {
        // 极端情况：prefsDir 不可读/不存在
        val prefsDir = prefsDir()
        prefsDir?.listFiles()?.forEach { it.delete() }
        prefsDir?.delete()
        // 不应抛异常
        try {
            StartupRecovery.performRecovery(ctx)
            assertTrue(true)
        } catch (e: Exception) {
            fail("performRecovery should not throw: $e")
        }
    }

    @Test
    fun `recovery flow - end to end - 4 starts crashes followed by recovery then success`() {
        // 模拟：连续 3 次启动 → 触发恢复 → 再启动 → 启动成功
        StartupRecovery.onStartupBegin(ctx)
        StartupRecovery.onStartupBegin(ctx)
        val third = StartupRecovery.onStartupBegin(ctx)
        assertTrue(third.shouldRecover)
        StartupRecovery.performRecovery(ctx)

        // 再次启动（应用已恢复）— 此时计数应从 0 开始
        val afterRecovery = StartupRecovery.onStartupBegin(ctx)
        assertFalse(afterRecovery.shouldRecover)
        assertEquals(1, afterRecovery.count)

        StartupRecovery.onStartupSuccess(ctx)
        val prefs = SafePreferences.get(ctx, StartupRecovery.PREFS_NAME)
        assertEquals(0, SafePreferences.getInt(prefs, StartupRecovery.KEY_CRASH_COUNT, -1))
    }

    private fun prefsDir(): File? = runCatching {
        ctx.getDir("shared_prefs", Context.MODE_PRIVATE)
    }.getOrNull() ?: File(ctx.filesDir.parentFile, "shared_prefs").also { it.mkdirs() }
}
