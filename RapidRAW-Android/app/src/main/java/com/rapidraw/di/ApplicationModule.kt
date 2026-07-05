package com.rapidraw.di

import android.content.Context
import com.rapidraw.core.ImageProcessor
import com.rapidraw.core.LutManager
import com.rapidraw.core.PresetManager
import com.rapidraw.core.SidecarManager
import com.rapidraw.data.repository.ExportQueueRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt Application 级依赖注入模块。
 *
 * ── Hilt 依赖注入迁移 (v2.51.1) ────────────────────────────────
 * 迁移步骤 5: 创建 ApplicationModule，提供全局单例依赖。
 *
 * @Module 标记这是一个 Hilt 模块，包含依赖提供方法。
 * @InstallIn(SingletonComponent::class) 使这些依赖在 Application 生命周期内可用。
 *
 * ── 提供的依赖 ─────────────────────────────────────────────────
 * - ImageProcessor: 图像处理核心引擎（每调用一次创建新实例，避免并发问题）
 * - SidecarManager: 编辑元数据持久化管理器
 * - LutManager: LUT 文件管理器
 * - PresetManager: 用户预设管理器
 * - ExportQueueRepository: 导出任务队列单例（Kotlin object）
 *
 * 注：SharedPreferences 不通过 Hilt 提供。
 * 项目中所有偏好设置统一使用 SafePreferences.get(context, name)，
 * 无需引入 androidx.preference 依赖。
 *
 * 注：FavoriteRepository / RecipeRepository / ProjectRepository 不在此处 @Provides，
 * 它们已通过 @Inject constructor + @Singleton 注解由 Hilt 自动构建。
 * 同时使用 @Provides + @Inject constructor 会触发 Hilt DuplicateBindings 编译错误。
 *
 * ── 与 DiContainer 共存策略 ───────────────────────────────────────
 * 向后兼容：保留 DiContainer 作为备选方案，逐步迁移：
 * 1. 新代码优先使用 Hilt @Inject 注入
 * 2. 旧代码可继续使用 DiContainer.resolve()
 * 3. 两套 DI 共存，不破坏现有功能
 * 4. 后续版本逐步将 DiContainer 依赖迁移至 Hilt
 *
 * ── 注意事项 ────────────────────────────────────────────────────
 * - ImageProcessor 不使用 @Singleton，每次注入创建新实例（避免状态共享）
 * - Repository 使用 @Singleton，确保全局唯一实例（数据一致性）
 * - Context 使用 @ApplicationContext 注解，避免 Activity Context 泄漏
 *
 * @since v1.10.0（Hilt DI 迁移）
 */
@Module
@InstallIn(SingletonComponent::class)
object ApplicationModule {

    /**
     * 提供 ImageProcessor 实例。
     *
     * 注意：每次注入创建新实例，不使用 @Singleton。
     * 原因：ImageProcessor 是有状态的（currentLut、处理参数等），
     * 多个 ViewModel 共享同一实例会导致状态冲突。
     *
     * 迁移说明：EditorViewModel 等需要图像处理的类可通过 @Inject 获取。
     * 替代原有手动创建 `ImageProcessor()` 或从 DiContainer 获取。
     */
    @Provides
    fun provideImageProcessor(): ImageProcessor {
        return ImageProcessor()
    }

    /**
     * 提供 SidecarManager 单例。
     *
     * 用途：管理编辑元数据的持久化（.rrdata 文件）。
     * 迁移说明：替代原有手动创建 `SidecarManager(context)`。
     *
     * @param context Application Context（通过 @ApplicationContext 注入）
     */
    @Provides
    @Singleton
    fun provideSidecarManager(
        @ApplicationContext context: Context
    ): SidecarManager {
        return SidecarManager(context)
    }

    /**
     * 提供 LutManager 单例。
     *
     * 用途：管理 LUT 文件的加载、导入、缓存。
     * 迁移说明：替代原有手动创建 `LutManager(context)`。
     *
     * @param context Application Context
     */
    @Provides
    @Singleton
    fun provideLutManager(
        @ApplicationContext context: Context
    ): LutManager {
        return LutManager(context)
    }

    /**
     * 提供 PresetManager 单例。
     *
     * 用途：管理用户自定义预设（保存、加载、删除、重命名）。
     * 迁移说明：替代原有手动创建 `PresetManager(context)`。
     *
     * @param context Application Context
     */
    @Provides
    @Singleton
    fun providePresetManager(
        @ApplicationContext context: Context
    ): PresetManager {
        return PresetManager(context)
    }

    // ── Repository 单例提供 ────────────────────────────────────────

    /**
     * 提供 ExportQueueRepository 单例。
     *
     * 用途：管理导出任务队列（添加、更新状态、移除任务）。
     * 注意：ExportQueueRepository 使用静态 StateFlow，此处提供方法
     * 主要用于 Hilt 测试环境 mock。
     */
    @Provides
    @Singleton
    fun provideExportQueueRepository(): ExportQueueRepository {
        return ExportQueueRepository
    }
}