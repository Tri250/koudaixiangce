package com.rapidraw.core

/**
 * 轻量级依赖注入容器（向后兼容备选方案）。
 *
 * ── Hilt 依赖注入迁移 (v2.51.1) ────────────────────────────────
 * 此 DiContainer 保留作为备选方案，与 Hilt 共存：
 *
 * 1. 向后兼容：旧代码可继续使用 DiContainer.resolve()
 * 2. 测试环境：单元测试无法使用 Hilt，需手动注入
 * 3. 逐步迁移：后续版本将逐步迁移至 Hilt @Inject
 *
 * ── 迁移策略 ────────────────────────────────────────────────
 * - 新代码优先使用 Hilt @Inject 注入
 * - ApplicationModule 提供 Hilt 单例依赖
 * - DiContainer 和 Hilt 可并存，不破坏现有功能
 * - 后续版本逐步废弃 DiContainer
 *
 * ── 使用示例 ────────────────────────────────────────────────
 * ```
 * // 注册
 * DiContainer.register<ImageProcessor> { ImageProcessor() }
 * // 获取
 * val processor = DiContainer.resolve<ImageProcessor>()
 * ```
 *
 * @since v1.10.0（正式版代码质量提升）
 * @deprecated 建议迁移至 Hilt @Inject 注入，后续版本将废弃
 */
@Deprecated(
    message = "建议迁移至 Hilt @Inject 注入，后续版本将废弃 DiContainer",
    replaceWith = ReplaceWith("@Inject constructor 注入，或使用 ApplicationModule 提供")
)
object DiContainer {

    @PublishedApi internal val registry = mutableMapOf<Class<*>, () -> Any>()
    @PublishedApi internal val singletons = mutableMapOf<Class<*>, Any>()

    /** 注册工厂函数（每次调用 resolve 都会创建新实例） */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> register(noinline factory: () -> T) {
        registry[T::class.java] = factory
    }

    /** 注册单例（首次 resolve 时创建，后续返回同一实例） */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> registerSingleton(noinline factory: () -> T) {
        registry[T::class.java] = {
            singletons.getOrPut(T::class.java) { factory() as Any }
        }
    }

    /** 解析依赖 */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> resolve(): T {
        val factory = registry[T::class.java]
            ?: throw IllegalStateException("No registration found for ${T::class.java.simpleName}")
        return factory() as T
    }

    /** 安全解析（返回 null 如果未注册） */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> resolveOrNull(): T? {
        return runCatching {
            val factory = registry[T::class.java] ?: return null
            factory() as T
        }.getOrNull()
    }

    /** 清除所有注册（仅用于测试） */
    fun reset() {
        registry.clear()
        singletons.clear()
    }
}