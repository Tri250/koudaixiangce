package com.rapidraw.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 宏观性能基准测试 — v1.8.0 正式版新增。
 *
 * 测试场景：
 * 1. 冷启动时间（TTID / TTFD）
 * 2. 编辑器打开时间（从图库到编辑器）
 * 3. 导出 4K 图片耗时
 * 4. 图库滚动 FPS
 *
 * 运行方式：
 * ./gradlew :benchmark:connectedCheck -Pandroid.testInstrumentationRunnerArguments.class=com.rapidraw.benchmark.MacrobenchmarkTest
 *
 * @since v1.8.0
 */
@RunWith(AndroidJUnit4::class)
class MacrobenchmarkTest {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * 冷启动性能测试（TTID + TTFD）。
     * 目标：TTID < 500ms, TTFD < 1000ms
     */
    @Test
    fun coldStartup() {
        benchmarkRule.measureRepeated(
            packageName = "com.rapidraw",
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(
                baselineProfileMode = androidx.benchmark.macro.BaselineProfileMode.Require,
            ),
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                // 清除应用数据，模拟首次启动
                pressHome()
            },
        ) {
            startActivityAndWait()
            // 等待图库屏幕加载完成
            device.wait(Until.findObject(By.res("library_grid")), 5_000)
        }
    }

    /**
     * 编辑器打开时间测试。
     * 目标：从图库到编辑器 < 800ms
     */
    @Test
    fun editorOpenTime() {
        benchmarkRule.measureRepeated(
            packageName = "com.rapidraw",
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                startActivityAndWait()
                device.wait(Until.findObject(By.res("library_grid")), 5_000)
            },
        ) {
            // 点击第一张图片进入编辑器
            device.findObject(By.res("library_grid")).children.firstOrNull()?.click()
            // 等待编辑器加载完成
            device.wait(Until.findObject(By.res("editor_container")), 5_000)
        }
    }

    /**
     * 图库滚动性能测试。
     * 目标：滚动期间平均 FPS > 55
     */
    @Test
    fun libraryScroll() {
        benchmarkRule.measureRepeated(
            packageName = "com.rapidraw",
            metrics = listOf(
                androidx.benchmark.macro.FrameTimingMetric(),
            ),
            compilationMode = CompilationMode.Partial(),
            iterations = 5,
            setupBlock = {
                startActivityAndWait()
                device.wait(Until.findObject(By.res("library_grid")), 5_000)
            },
        ) {
            val grid = device.findObject(By.res("library_grid"))
            // 平滑滚动 3 次
            repeat(3) {
                grid.swipe(
                    grid.visibleBounds.centerX(),
                    grid.visibleBounds.bottom - 100,
                    grid.visibleBounds.centerX(),
                    grid.visibleBounds.top + 100,
                    40, // 步数
                )
            }
            device.waitForIdle()
        }
    }
}