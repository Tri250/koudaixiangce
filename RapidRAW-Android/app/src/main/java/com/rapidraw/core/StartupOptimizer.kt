package com.rapidraw.core

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.profileinstaller.ProfileVerifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 启动优化器。
 *
 * 优化应用启动性能，确保快速响应用户体验：
 * - 基线配置文件生成和验证
 * - 懒加载策略
 * - 关键路径优化
 *
 * 适配 Android 16 的启动特性，配合 profileinstaller 库使用。
 */
object StartupOptimizer {

    private const val TAG = "StartupOptimizer"

    // ── 启动状态追踪 ──────────────────────────────────────────────────────

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _startupProgress = MutableStateFlow(StartupProgress.NOT_STARTED)
    val startupProgress = _startupProgress.asStateFlow()

    private val startupStartTime = AtomicLong(0)
    private val criticalPathStartTime = AtomicLong(0)
    private val uiReadyTime = AtomicLong(0)

    private val isInitialized = AtomicBoolean(false)

    /**
     * 启动进度状态。
     */
    enum class StartupProgress {
        NOT_STARTED,        // 未开始
        CRITICAL_PATH,      // 正在执行关键路径
        LAZY_LOADING,       // 正在执行懒加载
        READY,              // 启动完成，应用就绪
    }

    /**
     * 启动统计信息。
     */
    data class StartupStats(
        val totalStartupMs: Long,
        val criticalPathMs: Long,
        val lazyLoadingMs: Long,
        val profileInstalled: Boolean,
        val profileVersion: String,
    )

    // ── 基线配置文件 ──────────────────────────────────────────────────────

    /**
     * 验证基线配置文件是否已安装。
     *
     * 使用 ProfileVerifier API（Android 16 进一步优化），
     * 确认应用编译时优化是否生效。
     */
    suspend fun verifyBaselineProfile(context: Context): ProfileVerificationResult {
        return withContext(Dispatchers.IO) {
            try {
                val result = ProfileVerifier.verifyProfile(context)
                val isInstalled = result.profileInstalled
                val hasReferenceProfile = result.hasReferenceProfile

                Log.d(TAG, "Profile verification: installed=$isInstalled, hasReference=$hasReferenceProfile")

                ProfileVerificationResult(
                    profileInstalled = isInstalled,
                    hasReferenceProfile = hasReferenceProfile,
                    resultCode = result.resultCode,
                    message = "Profile status: ${if (isInstalled) "installed" else "not installed"}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Profile verification failed: ${e.message}")
                ProfileVerificationResult(
                    profileInstalled = false,
                    hasReferenceProfile = false,
                    resultCode = -1,
                    message = e.message ?: "Unknown error"
                )
            }
        }
    }

    data class ProfileVerificationResult(
        val profileInstalled: Boolean,
        val hasReferenceProfile: Boolean,
        val resultCode: Int,
        val message: String,
    )

    /**
     * 基线配置文件内容。
     *
     * 这个文件定义了应用启动时的关键代码路径，
     * 编译器会提前编译这些路径，减少启动时的 JIT 编译开销。
     *
     * 文件应位于: src/main/assets/profile.txt
     * 内容示例:
     * ```
     * Lcom/rapidraw/MainActivity;
     * Lcom/rapidraw/ui/editor/EditorScreen;
     * Lcom/rapidraw/ui/editor/EditorViewModel;
     * Lcom/rapidraw/core/GpuPipeline;
     * Lcom/rapidraw/core/ImageProcessor;
     * Lcom/rapidraw/ui/theme/Theme;
     * ```
     */
    object BaselineProfileRules {
        // 关键启动类（用于生成配置文件）
        val criticalStartupClasses = listOf(
            "Lcom/rapidraw/MainActivity;",
            "Lcom/rapidraw/RapidRawApp;",
            "Lcom/rapidraw/ui/editor/EditorScreen;",
            "Lcom/rapidraw/ui/editor/EditorViewModel;",
            "Lcom/rapidraw/ui/navigation/NavGraph;",
            "Lcom/rapidraw/ui/theme/Theme;",
            "Lcom/rapidraw/ui/theme/Color;",
            "Lcom/rapidraw/ui/theme/Type;",
            "Lcom/rapidraw/core/GpuPipeline;",
            "Lcom/rapidraw/core/ImageProcessor;",
            "Lcom/rapidraw/core/DeviceOptimizer;",
            "Lcom/rapidraw/data/model/Adjustments;",
        )

        // 关键方法
        val criticalStartupMethods = listOf(
            "Lcom/rapidraw/MainActivity;.onCreate",
            "Lcom/rapidraw/ui/editor/EditorViewModel;.initialize",
            "Lcom/rapidraw/core/GpuPipeline;.initialize",
            "Lcom/rapidraw/core/GpuPipeline;.compileShaders",
        )

        // 生成配置文件内容
        fun generateProfileContent(): String {
            return (criticalStartupClasses + criticalStartupMethods).joinToString("\n")
        }
    }

    // ── 关键路径优化 ──────────────────────────────────────────────────────

    /**
     * 关键路径任务定义。
     */
    data class CriticalTask(
        val name: String,
        val priority: Int,      // 优先级：数字越大越优先
        val requiresContext: Boolean,
        val block: suspend (Context?) -> Unit,
    )

    private val criticalTasks = mutableListOf<CriticalTask>()
    private val lazyTasks = mutableListOf<CriticalTask>()

    /**
     * 注册关键路径任务。
     *
     * 关键任务必须在 UI 显示前完成。
     */
    fun registerCriticalTask(name: String, priority: Int = 1, block: suspend (Context?) -> Unit) {
        criticalTasks.add(CriticalTask(name, priority, true, block))
    }

    /**
     * 注册懒加载任务。
     *
     * 懒任务可以在 UI 显示后异步执行。
     */
    fun registerLazyTask(name: String, priority: Int = 0, block: suspend (Context?) -> Unit) {
        lazyTasks.add(CriticalTask(name, priority, false, block))
    }

    // ── 启动流程控制 ──────────────────────────────────────────────────────

    /**
     * 始化启动优化器并执行关键路径。
     *
     * @param context 应用 Context
     * @param onCriticalPathComplete 关键路径完成回调（UI 可显示）
     */
    fun initialize(
        context: Context,
        onCriticalPathComplete: () -> Unit = {}
    ) {
        if (isInitialized.get()) {
            Log.w(TAG, "StartupOptimizer already initialized")
            return
        }

        startupStartTime.set(System.currentTimeMillis())
        _startupProgress.value = StartupProgress.CRITICAL_PATH
        criticalPathStartTime.set(System.currentTimeMillis())

        startupScope.launch {
            // 执行关键路径任务（按优先级排序）
            val sortedCriticalTasks = criticalTasks.sortedByDescending { it.priority }

            for (task in sortedCriticalTasks) {
                try {
                    Log.d(TAG, "Executing critical task: ${task.name}")
                    task.block(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Critical task failed: ${task.name}", e)
                }
            }

            val criticalPathMs = System.currentTimeMillis() - criticalPathStartTime.get()
            Log.d(TAG, "Critical path completed in $criticalPathMs ms")

            uiReadyTime.set(System.currentTimeMillis())
            _startupProgress.value = StartupProgress.LAZY_LOADING

            // 通知 UI 可以显示
            withContext(Dispatchers.Main) {
                onCriticalPathComplete()
            }

            // 执行懒加载任务
            executeLazyTasks(context)
        }

        isInitialized.set(true)
    }

    /**
     * 异步执行懒加载任务。
     */
    private suspend fun executeLazyTasks(context: Context) {
        val sortedLazyTasks = lazyTasks.sortedByDescending { it.priority }

        // 分批执行懒任务，避免一次性占用过多资源
        for (task in sortedLazyTasks) {
            // 每个任务间隔一小段时间，给主线程喘息空间
            delay(50)

            startupScope.launch {
                try {
                    Log.d(TAG, "Executing lazy task: ${task.name}")
                    task.block(if (task.requiresContext) context else null)
                } catch (e: Exception) {
                    Log.e(TAG, "Lazy task failed: ${task.name}", e)
                }
            }
        }

        // 懒加载完成
        val totalStartupMs = System.currentTimeMillis() - startupStartTime.get()
        Log.d(TAG, "Startup completed in $totalStartupMs ms")

        _startupProgress.value = StartupProgress.READY

        // 验证配置文件（异步）
        startupScope.launch {
            val profileResult = verifyBaselineProfile(context)
            Log.d(TAG, "Profile verification: ${profileResult.message}")
        }
    }

    // ── 懒加载策略 ──────────────────────────────────────────────────────

    /**
     * 懒加载持有者。
     *
     * 用于延迟初始化昂贵的对象，仅在首次使用时创建。
     */
    class LazyHolder<T>(
        private val initializer: () -> T,
        private val onInitialized: ((T) -> Unit)? = null
    ) {
        @Volatile
        private var instance: T? = null

        private val lock = Any()

        /**
         * 获取实例。
         *
         * 如果尚未初始化，会在首次调用时初始化。
         */
        fun get(): T {
            return instance ?: synchronized(lock) {
                instance ?: initializer().also {
                    instance = it
                    onInitialized?.invoke(it)
                    Log.d(TAG, "LazyHolder initialized: ${it?.let { it::class.simpleName }}")
                }
            }
        }

        /**
         * 是否已初始化。
         */
        fun isInitialized(): Boolean = instance != null

        /**
         * 预初始化（在后台线程执行）。
         */
        fun preload(scope: CoroutineScope = startupScope) {
            scope.launch {
                get()
            }
        }

        /**
         * 清理实例（用于内存压力场景）。
         */
        fun clear() {
            synchronized(lock) {
                instance = null
            }
        }
    }

    /**
     * 多实例懒加载持有者。
     *
     * 用于缓存多个同类对象，按需创建。
     */
    class LazyPool<K, V>(
        private val creator: (K) -> V,
        private val maxPoolSize: Int = 4,
        private val onItemCreated: ((K, V) -> Unit)? = null
    ) {
        private val pool = ConcurrentHashMap<K, V>()
        private val lock = Any()

        /**
         * 获取实例。
         */
        fun get(key: K): V {
            return pool.getOrPut(key) {
                val value = creator(key)
                onItemCreated?.invoke(key, value)
                Log.d(TAG, "LazyPool created item for key: $key")
                value
            }
        }

        /**
         * 获取实例（异步）。
         */
        suspend fun getAsync(key: K): V {
            return withContext(Dispatchers.Default) {
                get(key)
            }
        }

        /**
         * 是否已存在实例。
         */
        fun has(key: K): Boolean = pool.containsKey(key)

        /**
         * 移除实例。
         */
        fun remove(key: K): V? = pool.remove(key)

        /**
         * 清空池。
         */
        fun clear() = pool.clear()

        /**
         * 池大小。
         */
        fun size(): Int = pool.size
    }

    // ── 默认启动任务注册 ──────────────────────────────────────────────────

    /**
     * 注册默认启动任务。
     *
     * 包括初始化核心组件、性能优化器、内存管理器等。
     */
    fun registerDefaultTasks() {
        // 关键路径任务（必须完成）
        registerCriticalTask("DeviceOptimizer", priority = 10) { context ->
            context?.let {
                DeviceOptimizer.getRecommendedPreviewResolution()
            }
        }

        registerCriticalTask("PerformanceOptimizer", priority = 8) { context ->
            context?.let {
                PerformanceOptimizer.initializeBitmapPool(it)
                PerformanceOptimizer.is16KbPageSize()
                PerformanceOptimizer.getGpuMemoryStrategy(it)
            }
        }

        registerCriticalTask("MemoryManager", priority = 7) { context ->
            context?.let {
                val memoryManager = MemoryManager.getInstance(it)
                memoryManager.checkMemoryPressure()
                memoryManager.initializePreviewCache()
                memoryManager.registerSystemCallbacks()
            }
        }

        // 懒加载任务（后台执行）
        registerLazyTask("HdrDisplayManager", priority = 5) { context ->
            context?.let {
                val hdrManager = HdrDisplayManager(it)
                hdrManager.getHdrCapabilities()
            }
        }

        registerLazyTask("VulkanCapabilities", priority = 4) { context ->
            context?.let {
                PerformanceOptimizer.getVulkanCapabilities(it)
            }
        }

        registerLazyTask("PerformanceMetrics", priority = 3) { context ->
            context?.let {
                PerformanceOptimizer.getPerformanceMetrics(it)
            }
        }
    }

    // ── 启动统计 ──────────────────────────────────────────────────────

    /**
     * 获取启动统计信息。
     */
    suspend fun getStartupStats(context: Context): StartupStats {
        val profileResult = verifyBaselineProfile(context)
        val criticalMs = uiReadyTime.get() - criticalPathStartTime.get()
        val totalMs = System.currentTimeMillis() - startupStartTime.get()

        return StartupStats(
            totalStartupMs = totalMs,
            criticalPathMs = criticalMs,
            lazyLoadingMs = totalMs - criticalMs,
            profileInstalled = profileResult.profileInstalled,
            profileVersion = if (profileResult.profileInstalled) "installed" else "none"
        )
    }

    // ── 清理 ──────────────────────────────────────────────────────

    /**
     * 清理资源。
     */
    fun cleanup() {
        criticalTasks.clear()
        lazyTasks.clear()
        isInitialized.set(false)
        _startupProgress.value = StartupProgress.NOT_STARTED
    }
}