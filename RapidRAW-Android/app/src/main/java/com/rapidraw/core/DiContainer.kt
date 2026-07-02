package com.rapidraw.core

/**
 * 轻量级依赖注入容器。
 *
 * 避免引入 Hilt/Dagger/Koin 等重型 DI 框架，使用简单的
 * 单例注册表 + 工厂模式，满足当前项目的 DI 需求。
 *
 * 使用示例:
 * ```
 * // 注册
 * DiContainer.register<ImageProcessor> { ImageProcessor() }
 * // 获取
 * val processor = DiContainer.resolve<ImageProcessor>()
 * ```
 *
 * @since v1.10.0（正式版代码质量提升）
 */
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