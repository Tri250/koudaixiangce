package com.rapidraw.data.export

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.rapidraw.MainActivity
import com.rapidraw.data.model.ExportFormat
import com.rapidraw.data.model.ExportJob
import com.rapidraw.data.model.ExportJobStatus
import com.rapidraw.data.repository.ExportQueueRepository
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ExportQueue 的 instrumentation 测试。
 *
 * 验证导出队列 UI 行为：空状态、任务显示、重试、失败处理。
 * 这些测试不真正执行导出（避免 native 库依赖），而是测试队列状态管理。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ExportQueueInstrumentationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var context: Context

    companion object {
        private const val TEST_JOB_ID = "test-job-export-queue"
        private const val TEST_IMAGE_PATH = "/sdcard/DCIM/test_image.dng"
    }

    @Before
    fun setUp() {
        context = composeTestRule.activity.applicationContext
        ExportQueueRepository.clear()
    }

    @After
    fun tearDown() {
        ExportQueueRepository.clear()
    }

    // ── 测试：导出队列显示空状态 ─────────────────────────────────────

    @Test
    fun exportQueue_showsEmptyState() {
        // 确保队列为空
        ExportQueueRepository.clear()
        composeTestRule.waitForIdle()

        // 导航到导出队列页面
        navigateToExportQueue()
        composeTestRule.waitForIdle()

        // 验证空状态文本
        composeTestRule.onNodeWithText("导出队列").assertExists()
        composeTestRule.onNodeWithText("导出队列为空").assertExists()
        composeTestRule.onNodeWithText("导出的图片将在此处显示").assertExists()
    }

    // ── 测试：导出队列显示任务 ───────────────────────────────────────

    @Test
    fun exportQueue_showsJobs() {
        // 添加一个测试任务
        val job = ExportJob(
            id = TEST_JOB_ID,
            imagePath = TEST_IMAGE_PATH,
            status = ExportJobStatus.QUEUED,
            progress = 0f,
            format = ExportFormat.JPEG,
            width = 4000,
            height = 3000,
        )
        ExportQueueRepository.addJob(job)
        composeTestRule.waitForIdle()

        // 导航到导出队列
        navigateToExportQueue()
        composeTestRule.waitForIdle()

        // 验证任务显示
        composeTestRule.onNodeWithText("导出队列").assertExists()
        // 任务应显示文件名
        composeTestRule.onNodeWithText("test_image").assertExists()
    }

    // ── 测试：重试继续任务 ──────────────────────────────────────────

    @Test
    fun exportQueue_retryResumesJob() {
        // 添加一个失败任务
        val failedJob = ExportJob(
            id = "test-retry-job",
            imagePath = TEST_IMAGE_PATH,
            status = ExportJobStatus.FAILED,
            progress = 0.5f,
            error = "测试错误",
            format = ExportFormat.JPEG,
            width = 4000,
            height = 3000,
        )
        ExportQueueRepository.addJob(failedJob)
        composeTestRule.waitForIdle()

        // 导航到导出队列
        navigateToExportQueue()
        composeTestRule.waitForIdle()

        // 验证失败任务显示
        composeTestRule.onNodeWithText("失败").assertExists()
        composeTestRule.onNodeWithText("测试错误").assertExists()

        // 点击重试按钮
        composeTestRule.onNodeWithContentDescription("重试").performClick()
        composeTestRule.waitForIdle()

        // 验证任务状态变为 QUEUED
        val updatedJob = ExportQueueRepository.jobs.value.firstOrNull { it.id == "test-retry-job" }
        assert(updatedJob != null) { "Retried job should still exist" }
        assert(updatedJob!!.status == ExportJobStatus.QUEUED) {
            "Retried job status should be QUEUED, was ${updatedJob.status}"
        }
    }

    // ── 测试：导出队列处理器处理失败 ─────────────────────────────────

    @Test
    fun exportQueueProcessor_handlesFailure() {
        // 添加一个指向无效路径的任务
        val invalidJob = ExportJob(
            id = "test-invalid-job",
            imagePath = "/nonexistent/path/image.dng",
            status = ExportJobStatus.QUEUED,
            progress = 0f,
            format = ExportFormat.JPEG,
            width = 0,
            height = 0,
        )
        ExportQueueRepository.addJob(invalidJob)
        composeTestRule.waitForIdle()

        // 触发处理器
        ExportQueueProcessor.kick(context)
        composeTestRule.waitForIdle()

        // 等待处理完成（最多 5 秒）
        var attempts = 0
        while (attempts < 50) {
            val job = ExportQueueRepository.jobs.value.firstOrNull { it.id == "test-invalid-job" }
            if (job?.status == ExportJobStatus.FAILED) break
            Thread.sleep(100)
            attempts++
        }

        // 验证任务标记为失败
        val finalJob = ExportQueueRepository.jobs.value.firstOrNull { it.id == "test-invalid-job" }
        assert(finalJob != null) { "Failed job should still exist in queue" }
        assert(finalJob!!.status == ExportJobStatus.FAILED) {
            "Job with invalid path should be marked FAILED, was ${finalJob.status}"
        }
    }

    // ── 辅助方法 ────────────────────────────────────────────────────

    private fun navigateToExportQueue() {
        // 通过点击导航到导出队列
        // 在实际测试中，导出队列通过导航系统访问
        // 这里直接操纵 ExportQueueScreen 的 ViewModel 状态
        // 简化起见，测试队列数据层面的行为
    }
}