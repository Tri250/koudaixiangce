# RapidRAW Android 安装与启动专项测试清单 (v2026.07)

> **项目名称**: RapidRAW — 专业级 RAW 照片编辑器  
> **测试范围**: Android 端安装/部署、冷启动、热启动、权限、引导、导航、崩溃恢复、兼容性、性能  
> **测试目标**: 2026 最高水平全链路覆盖，零遗漏  
> **测试基线**: minSdk=26, targetSdk=36, compileSdk=36  
> **构建变体**: dev / staging / prod  
> **测试环境**: 真机 + 模拟器 + OEM 厂商设备 (OPPO Find X8/X9, Samsung Galaxy S24/25, Xiaomi 14/15, Google Pixel 8/9, Huawei, Vivo, OnePlus, Motorola, ASUS, Sony, Nothing)

---

## 目录

1. [APK 安装与部署 (25项)](#1-apk-安装与部署)
2. [Application 初始化链路 (40项)](#2-application-初始化链路)
3. [MainActivity 启动与首帧渲染 (30项)](#3-mainactivity-启动与首帧渲染)
4. [权限系统 (20项)](#4-权限系统)
5. [引导流程 Onboarding (25项)](#5-引导流程-onboarding)
6. [导航与路由 (20项)](#6-导航与路由)
7. [崩溃恢复与诊断 (30项)](#7-崩溃恢复与诊断)
8. [ANR / 死锁检测 (20项)](#8-anr--死锁检测)
9. [进程死亡与恢复 (15项)](#9-进程死亡与恢复)
10. [兼容性矩阵 (30项)](#10-兼容性矩阵)
11. [性能与启动指标 (20项)](#11-性能与启动指标)
12. [安全与完整性 (20项)](#12-安全与完整性)
13. [构建变体与签名 (20项)](#13-构建变体与签名)
14. [ProGuard/R8 混淆 (20项)](#14-proguardr8-混淆)
15. [Native 库加载 (15项)](#15-native-库加载)
16. [通知渠道 (10项)](#16-通知渠道)
17. [主题与配置 (15项)](#17-主题与配置)
18. [外部 Intent 与深层链接 (20项)](#18-外部-intent-与深层链接)
19. [App Shortcuts (10项)](#19-app-shortcuts)
20. [内存与缓存 (15项)](#20-内存与缓存)
21. [数据库初始化 (10项)](#21-数据库初始化)
22. [废弃归属与回归测试 (按热修复版本) (20项)](#22-废弃归属与回归测试)

---

## 1. APK 安装与部署

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 1.1 | **首次安装 (Clean Install)** | P0 | 卸载旧版 → 安装新版 → 启动 | 安装成功，无签名冲突，首次启动进入引导页 | dev/staging/prod 三种变体各测 |
| 1.2 | **覆盖安装 (Upgrade)** | P0 | v1.5.5 → v1.6.8 覆盖安装 | 数据不丢失 (onboarding/权限/收藏/编辑历史)，SharedPreferences 无损坏 | SafePreferences 需正确迁移 |
| 1.3 | **跨版本升级** | P0 | v1.0.0 → v1.6.8 大版本跨越 | 崩溃计数器 ≤ 2，不触发 StartupRecovery 误清数据 | 白名单 prefs 不能丢失 |
| 1.4 | **降级覆盖安装** | P1 | 高版本 → 低版本覆盖安装 | 系统阻止或提示降级；若强行安装，数据库迁移不回退 | versionCode 降级场景 |
| 1.5 | **adb install -r 覆盖安装** | P1 | adb install -r 反复安装 | 10 次覆盖安装无 SharedPreferences 损坏 | SafePreferences 容错 |
| 1.6 | **adb install -d 降级安装** | P2 | adb install -d 降级安装 | 降级后数据库版本不匹配也应优雅降级而非崩溃 | Room fallbackToDestructiveMigration |
| 1.7 | **APK 签名校验** | P0 | 安装后 SecurityProvider.verifyAppSignature() 调用 | PASS，签名指纹匹配预期 | 防止二次打包/篡改 |
| 1.8 | **安装包体积检查** | P1 | 检查 APK 体积是否在预期范围内 | ABI split 后 arm64-v8a < 150MB | 含 native .so + ML 模型 |
| 1.9 | **存储空间不足时安装** | P1 | 模拟剩余空间 < 2x APK 大小 | 系统提示空间不足，不会残留损坏文件 | 边缘场景 |
| 1.10 | **SD 卡安装** | P2 | 部分 OEM 允许安装到 SD 卡 | 应用功能正常，native 库路径正确 | 华为/小米旧设备 |
| 1.11 | **Play Store 分发自更新** | P0 | 通过 Google Play 分发自更新 | InAppUpdateManager 正常触发，下载与安装完成 | 依赖 Play Core |
| 1.12 | **应用分身/双开支持** | P2 | MIUI/ColorOS 应用双开 | 第二个实例功能正常，不互相干扰 SharedPreferences | 双开时包名相同 |
| 1.13 | **Android 16 (API 36) 16KB 页面大小** | P0 | Pixel 8+/OPPO Find X9+ 设备安装 | native 库 16KB 对齐，不崩溃 | packaging.jniLibs.useLegacyPackaging = false |
| 1.14 | **Android 15+ edge-to-edge 默认** | P0 | API 35+ 设备安装启动 | 内容自动绘制到系统栏区域，无黑边 | enableEdgeToEdge() |
| 1.15 | **多用户环境安装** | P2 | Android 多用户/工作资料 | 每个用户独立安装，数据隔离 | 工作资料可能限制某些功能 |
| 1.16 | **adb install 多 ABI 兼容** | P1 | x86_64 模拟器 | arm64-v8a native 库在 x86_64 模拟器上通过 Houdini 翻译 | 应在 x86_64 模拟器上测试 |
| 1.17 | **安装时 Manifest 权限合并** | P1 | 检查最终合并的 Manifest | 无意外权限 (如 READ_PHONE_STATE 已移除)，POST_NOTIFICATIONS 已声明 | tools:node="remove" |
| 1.18 | **安装时 FileProvider 注册** | P0 | 检查 FileProvider 正确注册 | authorities=${applicationId}.fileprovider 正确 | 与 dev/staging 变体包名一致 |
| 1.19 | **安装时 network_security_config 生效** | P1 | 抓包验证流量 | release 不使用 cleartext，dev 可以宽松 | 防止中间人攻击 |
| 1.20 | **安装时 dataExtractionRules 正确** | P2 | Android 12+ 备份与恢复 | 应用数据按规则正确提取与恢复 | @xml/data_extraction_rules |
| 1.21 | **安装时 allowBackup=false 验证** | P1 | 验证 manifest 配置 | 不自动备份应用数据（避免隐私泄露） | android:allowBackup="false" |
| 1.22 | **安装时 largeHeap=true 申请** | P1 | 验证 manifest 配置 | 应用获得更大堆内存（RAW 处理需要） | android:largeHeap="true" |
| 1.23 | **安装时 localeConfig 多语言** | P1 | 验证 manifest 配置 | 系统语言切换时应用跟随 | @xml/locale_config (zh, en, ja, ko) |
| 1.24 | **安装时 supportsRtl=true** | P2 | 阿拉伯语等 RTL 语言环境 | 布局正确翻转，无 UI 错乱 | android:supportsRtl="true" |
| 1.25 | **CI 签名注入 (KEYSTORE_BASE64)** | P0 | CI 环境构建 release APK | 从环境变量解码 keystore 成功，签名正确 | build.gradle.kts 中 CI 签名逻辑 |

---

## 2. Application 初始化链路

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 2.1 | **RapidRawApp.onCreate 完整执行** | P0 | 冷启动追踪 Timber 日志 | onCreate 无异常，所有 CRITICAL 任务完成，logcat 无 ERROR | 核心路径 |
| 2.2 | **StartupOptimizer CRITICAL 级任务执行** | P0 | 检查 8 个 CRITICAL 任务是否全部执行 | CrashHandler/SecurityProvider/NotificationChannels/CrashReporter/ANRWatchdog/StrictMode/FontScale/PerformanceMonitor 全部初始化 | 同步执行 |
| 2.3 | **StartupOptimizer HIGH 级任务延迟执行** | P1 | 通过 IdleHandler 检查 | Analytics/Billing 在首帧后执行，不阻塞首帧 | 延迟到主线程空闲 |
| 2.4 | **StartupOptimizer MEDIUM 级任务 2s 后执行** | P1 | 检查 NetworkCache/SystemCompatibility/OemCompatibility 在 2s 后执行 | 日志中 MEDIUM 任务时间戳 > 2s | postDelayed 2000ms |
| 2.5 | **StartupOptimizer LOW 级任务 5s 后执行** | P1 | 检查 PlayIntegrity 在 5s 后执行 | 日志中 LOW 任务时间戳 > 5s | postDelayed 5000ms |
| 2.6 | **StartupOptimizer 重复执行防护** | P1 | 调用 execute() 两次 | 第二次打印 WARN 日志，不重复执行 | AtomicBoolean 防重入 |
| 2.7 | **StartupOptimizer.shutdown() 取消延迟任务** | P1 | 在 execute() 后立即 shutdown | MEDIUM/LOW 任务不执行 | Handler.removeCallbacks |
| 2.8 | **CrashHandler.install 最早执行** | P0 | 验证 CrashHandler 在 StartupOptimizer 第一个 CRITICAL 任务中 | 所有后续异常都能被捕获 | 核心容错 |
| 2.9 | **CrashHandler 二次安装幂等** | P1 | 调用 CrashHandler.install() 两次 | 不抛异常，不重复注册 handler | 幂等性 |
| 2.10 | **SecurityProvider.verifyAppSignature 启动时执行** | P0 | 检查签名校验结果 | 签名校验通过，日志记录签名 SHA-256 | CRITICAL 级 |
| 2.11 | **SecurityProvider 签名校验失败处理** | P1 | 修改签名后安装 | 虽然 CRITICAL 任务用 runCatching 包裹，但日志应记录失败 | 不崩溃但记录 |
| 2.12 | **NotificationChannels.initialize 创建 3 个渠道** | P0 | 检查系统通知设置中渠道 | export_progress/sync/update 三个渠道可见 | Android 8.0+ |
| 2.13 | **NotificationChannels.initialize 在 API < 26 跳过** | P2 | minSdk 26，理论上不会触发 | 不抛异常 | 防御性代码 |
| 2.14 | **CrashReporter.init 初始化** | P0 | 启动后 logcat 检查 | CrashReporter 初始化完成，上报队列就绪 | 异步上报 |
| 2.15 | **ANRWatchdog.start 启动** | P0 | 检查 ANRWatchdog 线程启动 | 监控线程 running，主线程 tick 正常响应 | 默认 2s 阈值 |
| 2.16 | **StrictMode 仅在 Debug 包启用** | P1 | 对比 dev 和 prod 构建 | Debug 包 StrictMode 检测主线程 IO/网络；Release 包零开销 | BuildConfig.DEBUG 判断 |
| 2.17 | **StrictMode Android 14+ 检测 unsafe intent launch** | P1 | API 34+ 设备 | detectUnsafeIntentLaunch 启用 | Android 14+ 特性 |
| 2.18 | **FontScale 限制生效** | P0 | 系统设置超大字体 → 启动应用 | 字体缩放 ≤ 1.3x，UI 不溢出 | attachBaseContext 中限制 |
| 2.19 | **FontScale 运行时变化** | P1 | 启动后系统设置改超大字体 | Activity.recreate() 触发，重新应用限制 | onConfigurationChanged 中检测 |
| 2.20 | **PerformanceMonitor.init 初始化** | P0 | 检查帧率监控和热监控启动 | JankStats 创建，ThermalStatusListener 注册 | CRITICAL 级 |
| 2.21 | **AnalyticsManager.init 延迟执行** | P1 | 检查 Analytics 初始化时间 | 不阻塞首帧，在 IdleHandler 中执行 | HIGH 级 |
| 2.22 | **BillingManager.init + connect 延迟执行** | P1 | 检查 Billing 连接状态 | 首帧后再连接 Google Play Billing | HIGH 级 |
| 2.23 | **NetworkCache 初始化** | P2 | 检查 OkHttp 缓存目录创建 | MEDIUM 级，2s 后执行 | MEDIUM 级 |
| 2.24 | **SystemCompatibility 报告生成** | P1 | 检查 CompatibilityReport 日志 | 设备 API 级别、特性支持、建议列表完整 | MEDIUM 级 |
| 2.25 | **OemCompatibility OEM 检测** | P1 | 各 OEM 设备上验证 | 正确识别 MIUI/ColorOS/OneUI/EMUI 等 | MEDIUM 级 |
| 2.26 | **BackgroundCompatibility StandbyBucket 检测** | P2 | 检查 App Standby Bucket | 日志输出当前 bucket 名称 | MEDIUM 级 |
| 2.27 | **PlayIntegrityHelper.checkIntegrity 延迟执行** | P1 | 检查完整性检查结果 | 5s 后执行，结果缓存 5 分钟 | LOW 级 |
| 2.28 | **DeadlockDetector.start 启动** | P0 | 启动时检查死锁检测线程 | 守护线程 running，每 2s 检查主线程 | 在 StartupOptimizer 之前启动 |
| 2.29 | **StartupRecovery.onStartupBegin 崩溃计数** | P0 | 检查崩溃计数器 | 首次启动 counter=1，成功后清零 | 防止启动循环 |
| 2.30 | **StartupRecovery 连续崩溃恢复** | P0 | 连续崩溃 3 次 → 第 4 次启动 | 触发 performRecovery，清理 cache 和白名单外 prefs | 阈值 CRASH_THRESHOLD=3 |
| 2.31 | **StartupRecovery 白名单保护** | P0 | 崩溃恢复后验证白名单 prefs | onboarding/permission_history/pending_uri/startup 四个 prefs 不丢失 | PRESERVED_PREFS |
| 2.32 | **StartupRecovery.onStartupSuccess 清零** | P0 | 正常启动完成后验证 | 崩溃计数器归零 | 在 StartupOptimizer.execute() 之后 |
| 2.33 | **LeakCanary 仅 Debug 启用** | P1 | 对比 dev 和 prod 构建 | Debug 包启用 LeakCanary；Release 包零开销 | 反射调用，无编译依赖 |
| 2.34 | **Per-app Language 设置** | P1 | 验证默认语言 zh-CN | API 33+ 使用 LocaleManager；API 26-32 使用 AppCompatDelegate | 主线程执行 |
| 2.35 | **Per-app Language OEM 兼容性** | P1 | MIUI/ColorOS/OneUI 设备上验证 | 语言设置不触发 ANR 检测误报 | 2026.07 hotfix：回退到主线程 |
| 2.36 | **ActivityLifecycleCallbacks 注册** | P0 | 前台/后台切换验证 | isAppForeground 状态正确，AtomicInteger 计数准确 | 并发安全 |
| 2.37 | **onTrimMemory 缓存清理** | P1 | 系统内存压力模拟 | 后台时清理 raw_decode 缓存和 thumbnail 缓存 | 守护线程执行 |
| 2.38 | **onTerminate 资源释放** | P1 | 模拟进程退出 | StartupOptimizer/BillingManager/AnalyticsManager/CrashReporter/PerformanceMonitor/ExportQueueProcessor/DeadlockDetector/ANRWatchdog 全部 shutdown | 最佳努力清理 |
| 2.39 | **logDeviceCapabilities 日志** | P2 | 检查启动日志 | 设备制造商、型号、Android 版本、ABI、内存 | 诊断用途 |
| 2.40 | **attachBaseContext 字体缩放限制** | P0 | 系统字体 200% → 启动应用 | 限制到 1.3x，UI 不溢出 | 最早时机 |

---

## 3. MainActivity 启动与首帧渲染

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 3.1 | **MainActivity.onCreate 完整执行** | P0 | 冷启动，logcat 追踪 | 无异常，setContent 成功，RapidRawTheme 渲染 | 核心路径 |
| 3.2 | **Pre-onCreate 异常兜底** | P0 | 模拟 super.onCreate 前异常 | 异常被捕获到 logcat，不闪退到桌面 | Thread.setDefaultUncaughtExceptionHandler |
| 3.3 | **enableEdgeToEdge 成功** | P0 | API 35+ 设备 | 系统栏透明，内容绘制到系统栏后 | 2026 Android 默认 |
| 3.4 | **enableEdgeToEdge OEM 兼容性** | P1 | MIUI/ColorOS 设备 | try-catch 保护，失败不崩溃 | 部分 OEM 支持不完整 |
| 3.5 | **setDecorFitsSystemWindows(false)** | P0 | 验证窗口标志 | 内容不自动偏移，由 Compose Insets 处理 | try-catch 保护 |
| 3.6 | **Immersive Mode 沉浸式模式** | P1 | 验证状态栏/导航栏透明 | 系统栏颜色透明，行为正确 | 分 API 版本处理 |
| 3.7 | **Predictive Back 手势 (Android 14+)** | P0 | API 34+ 设备，侧滑返回 | 预测性返回动画正常，手势不冲突 | enableOnBackInvokedCallback="true" |
| 3.8 | **BackHandler 自定义逻辑** | P0 | Library/Onboarding 页面按返回键 | Library/Onboarding 不拦截，其他页面返回 Library | Predictive Back 支持 |
| 3.9 | **setContent 异常降级 UI** | P0 | 模拟 Compose 组合异常 | 展示黑底白字降级 UI "启动遇到问题，请重启或反馈" | try-catch 保护 |
| 3.10 | **降级 UI 再次失败最终重启** | P1 | 模拟降级 UI 也失败 | finish + 重启 Activity | 最后兜底 |
| 3.11 | **权限请求延迟 300ms** | P0 | 验证权限请求不阻塞首帧 | 权限弹窗在首帧渲染后 300ms 出现 | lifecycleScope.launch |
| 3.12 | **权限请求阶段异常兜底** | P1 | 模拟权限请求抛异常 | 异常被 CrashHandler.coroutineExceptionHandler 处理 | 不崩溃 |
| 3.13 | **pendingImageUri 持久化** | P0 | 外部 Intent 打开图片 | URI 持久化到 SafePreferences，进程死亡后恢复 | 2026.07 hotfix |
| 3.14 | **pendingImageUriState mutableStateOf** | P0 | 外部 Intent 触发导航 | LaunchedEffect 观察到 state 变化，正确导航到 Editor | 2026.06 hotfix |
| 3.15 | **pendingImageUri 消费后清空** | P0 | 导航到 Editor 后 | URI 从 state 和 prefs 中清除，防止重复导航 | setPendingImageUri(null) |
| 3.16 | **restorePendingImageUri 进程死亡恢复** | P0 | 模拟进程死亡 → Activity 重建 | 从 prefs 恢复 URI，LaunchedEffect 触发导航 | 2026.07 新增 |
| 3.17 | **onNewIntent 处理外部图片** | P0 | 从图库/文件管理器分享图片到应用 | 正确导航到 Editor，URI 编码/解码正确 | 支持多种 RAW 格式 |
| 3.18 | **onNewIntent Onboarding 状态判断** | P0 | 引导未完成时收到外部 Intent | 缓存 URI，等待引导完成后再导航 | 防止绕过引导 |
| 3.19 | **onNewIntent 重复 URI 防护** | P1 | 两次打开同一图片 | 第二次跳过导航，日志 "same URI, skip" | 防止重复打开 |
| 3.20 | **onConfigurationChanged 处理** | P0 | 旋转屏幕/折叠屏/深色模式切换 | Activity 不重建，Compose 自动重组，日志记录 | configChanges 声明 |
| 3.21 | **onConfigurationChanged 字体缩放限制** | P1 | 运行时系统字体超出 1.3x | Activity.recreate() 触发，限制生效 | 2026.07 新增 |
| 3.22 | **onMultiWindowModeChanged 多窗口** | P1 | 进入/退出分屏模式 | 日志记录，Compose WindowSizeClass 自适应 | 分屏/多窗口 |
| 3.23 | **App Shortcut Intent 处理** | P1 | 长按图标 → 选择快捷方式 | library/recent_project/new_edit 导航正确 | handleShortcutIntent |
| 3.24 | **Shortcut 延迟 300ms 导航** | P1 | 验证 delay 300ms 等待 Compose | NavController 已就绪后再导航 | 防止 NavController 为 null |
| 3.25 | **navigateToEditor 无效 URI 防御** | P1 | 传入空字符串或非法 URI | 返回 Library，不崩溃 | URL 解码 + 空值检查 |
| 3.26 | **navigateToEditor 重复导航防护** | P1 | 已在 Editor 页面时再次导航 | 比较 URI，相同则跳过；不同则 popBackStack 后重新导航 | 防止导航栈堆积 |
| 3.27 | **ColorOS/OPPO EDIT_IMAGE Intent** | P1 | ColorOS 相册 → 编辑图片 | 正确接收 Intent 并导航到 Editor | com.coloros.gallery3d.action.EDIT_IMAGE |
| 3.28 | **rapidraw:// 深层链接** | P0 | 浏览器/外部应用点击 rapidraw://editor/{path} | 正确解析深层链接，导航到 Editor | deepLinks 声明 |
| 3.29 | **rapidraw://editor_uri/{uri} 深层链接** | P0 | 外部应用通过 URI 打开 | 正确解析 URI，导航到 Editor | deepLinks 声明 |
| 3.30 | **首帧渲染时间 (TTID)** | P0 | Systrace/Perfetto 测量 | 冷启动 < 500ms (旗舰设备)，热启动 < 200ms | Baseline Profile 预编译 |

---

## 4. 权限系统

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 4.1 | **Android 14+ (API 34) 权限请求** | P0 | 首次启动 | 请求 READ_MEDIA_IMAGES + READ_MEDIA_VISUAL_USER_SELECTED + POST_NOTIFICATIONS | 精确声明 |
| 4.2 | **Android 13 (API 33) 权限请求** | P0 | 首次启动 | 请求 READ_MEDIA_IMAGES + POST_NOTIFICATIONS | 精确声明 |
| 4.3 | **Android 10-12 (API 29-32) 权限请求** | P0 | 首次启动 | 请求 READ_EXTERNAL_STORAGE + POST_NOTIFICATIONS (13+) | 传统权限 |
| 4.4 | **Android 8-9 (API 26-28) 权限请求** | P0 | 首次启动 | 请求 READ_EXTERNAL_STORAGE | 基本权限 |
| 4.5 | **权限全部授予** | P0 | 用户点击"授权" | 正常进入 Library，图库可浏览 | 正常流程 |
| 4.6 | **权限部分拒绝** | P0 | 用户拒绝部分权限 | 日志记录拒绝的权限，图库功能受限但可继续使用 | 不阻塞启动 |
| 4.7 | **权限永久拒绝** | P0 | 用户拒绝 + "不再询问" | Toast 提示 + 引导到系统设置页面 | Settings.ACTION_APPLICATION_DETAILS_SETTINGS |
| 4.8 | **首次拒绝 vs 永久拒绝区分** | P0 | 验证 shouldShowRationale + 历史记录 | 首次拒绝：不触发设置跳转；永久拒绝：触发设置跳转 | permission_history prefs |
| 4.9 | **POST_NOTIFICATIONS 权限 (Android 13+)** | P0 | 首次启动请求通知权限 | 通知权限请求弹窗，拒绝后 WorkManager 前台任务仍可运行 | 2026 hotfix 必须 |
| 4.10 | **通知权限拒绝后导出完成通知** | P1 | 拒绝通知权限 → 执行导出 | 导出完成但无通知，日志中记录 | 降级处理 |
| 4.11 | **Onboarding 中权限引导页** | P0 | 首次启动 → 引导页第 2 页 | 显示"访问你的照片"引导，点击"授权访问"触发权限请求 | PermissionPage |
| 4.12 | **Onboarding 权限跳过** | P0 | 引导页点击"稍后授权" | 跳过权限请求，进入 Library，后续可在设置中授权 | 不阻塞 |
| 4.13 | **Onboarding 权限已全部授予** | P1 | 引导页但权限已全部授予 | 直接进入下一页，不显示权限请求弹窗 | 智能判断 |
| 4.14 | **FOREGROUND_SERVICE 权限声明** | P1 | Manifest 检查 | FOREGROUND_SERVICE / FOREGROUND_SERVICE_MEDIA_PROCESSING / FOREGROUND_SERVICE_DATA_SYNC 已声明 | Android 14+ 必须 |
| 4.15 | **REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 权限** | P1 | 导出时请求电池优化豁免 | 跳转到系统设置，用户可豁免 | 长时间导出需要 |
| 4.16 | **READ_PHONE_STATE 权限移除验证** | P1 | 检查合并 Manifest | 最终 APK 不包含 READ_PHONE_STATE 权限 | tools:node="remove" |
| 4.17 | **权限请求后 markPermissionsRequested 记录** | P1 | 检查 permission_history prefs | 已请求的权限列表持久化 | 用于区分首次/永久拒绝 |
| 4.18 | **权限变更后应用行为** | P1 | 系统设置中撤销权限 → 回到应用 | 应用检测到权限缺失，功能降级但不崩溃 | 运行时权限检测 |
| 4.19 | **OPPO Find 设备 ColorOS 权限兼容** | P1 | OPPO Find X8/X9 | ColorOS 特定权限管理不干扰应用 | OEM 兼容 |
| 4.20 | **MIUI/HyperOS 权限管理兼容** | P1 | Xiaomi 14/15 | MIUI 自启动/后台弹出权限不干扰应用 | OEM 兼容 |

---

## 5. 引导流程 Onboarding

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 5.1 | **首次启动显示引导页** | P0 | 清除数据 → 启动 | 显示 5 页引导 (Welcome/Permission/Feature×3) | OnboardingState.isCompleted=false |
| 5.2 | **已完成引导的用户跳过引导** | P0 | 引导完成后再次冷启动 | 直接进入 Library，不闪现引导页 | 2026.07 单源事实 |
| 5.3 | **OnboardingState 单源事实** | P0 | 验证 NavGraph 和 OnboardingViewModel 读到同一值 | 冷启动时 rememberStartDestination 正确判断 | 防止竞态 |
| 5.4 | **Welcome 页 — 品牌展示** | P1 | 验证第 1 页 | Logo "R" 渐变圆角方块 + "RapidRAW" 标题 + "开始使用"按钮 | 视觉验证 |
| 5.5 | **Welcome 页 — 点击"开始使用"** | P1 | 点击按钮 | 滑到第 2 页 (Permission 页) | 动画过渡 |
| 5.6 | **Permission 页 — 授权按钮** | P0 | 点击"授权访问" | 触发权限请求，授予后滑到第 3 页 | 权限请求 |
| 5.7 | **Permission 页 — 跳过按钮** | P0 | 点击"稍后授权" | 跳过权限，滑到第 3 页 | 不阻塞 |
| 5.8 | **Feature 页 1 — 智能优化** | P1 | 验证第 3 页 | 显示 ✨ 图标 + "智能优化" + 描述 | 视觉验证 |
| 5.9 | **Feature 页 2 — 专业调色** | P1 | 验证第 4 页 | 显示 🎨 图标 + "专业调色" + 描述 | 视觉验证 |
| 5.10 | **Feature 页 3 — 胶片模拟** | P1 | 验证第 5 页 | 显示 📷 图标 + "胶片模拟" + 描述 + "开始编辑"按钮 | 最后一页 |
| 5.11 | **Feature 页 — 点击"开始编辑"** | P0 | 最后一页点击按钮 | 触发 completeOnboarding() → navigateOnce() → 进入 Library | 单次导航防护 |
| 5.12 | **completeOnboarding 双写** | P0 | 验证 OnboardingState 和 ViewModel 写入 | SafePreferences + OnboardingState.markCompleted 都更新 | OnboardingState 是单源 |
| 5.13 | **navigateOnce 防重复调用** | P0 | 快速双击"开始编辑" | 只导航一次，不触发 Navigation Compose 二次导航崩溃 | hasNavigated 标志 |
| 5.14 | **引导页跳过按钮** | P0 | 点击右上角"跳过" | completeOnboarding → navigateOnce → 进入 Library | 任何页都可跳过 |
| 5.15 | **引导页"下一步"按钮** | P1 | 点击"下一步" | 滑到下一页，动画过渡 | animateScrollToPage |
| 5.16 | **引导页 PageIndicator 圆点** | P1 | 验证 5 个圆点 | 当前页圆点高亮 (HasselbladOrange)，其他页灰色 | 视觉验证 |
| 5.17 | **引导页 HorizontalPager 滑动** | P1 | 左右滑动切换页面 | 水平滑动流畅，无卡顿 | 手势交互 |
| 5.18 | **引导页返回键行为** | P1 | 引导页按返回键 | BackHandler 不拦截，系统返回行为 | Predictive Back 兼容 |
| 5.19 | **引导页深色模式** | P1 | 系统深色模式 → 引导页 | EditorBackground 深色背景适配 | Dark Theme |
| 5.20 | **引导页旋转屏幕** | P1 | 引导页旋转屏幕 | 页面状态保持，不崩溃 | 配置变更 |
| 5.21 | **引导页分屏模式** | P2 | 引导页进入分屏 | 布局自适应，不崩溃 | 多窗口 |
| 5.22 | **引导页大字体/辅助功能** | P1 | 系统超大字体 → 引导页 | 字体限制 1.3x，文字不溢出 | 无障碍 |
| 5.23 | **引导页进程死亡恢复** | P1 | 引导中模拟进程死亡 | 恢复后引导状态保持，不会已完成的用户重看引导 | 2026.07 改进 |
| 5.24 | **OnboardingState.clear 测试辅助** | P2 | 测试中调用 clear() | 引导状态清除，下次启动重看引导 | 仅测试使用 |
| 5.25 | **引导页冷启动性能** | P1 | 首次启动引导页渲染时间 | 引导页 < 500ms 显示 | 不包含权限请求等待 |

---

## 6. 导航与路由

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 6.1 | **Library 页面启动** | P0 | 已完成引导 → 启动 | 直接显示 Library 页面 | 默认起始页 |
| 6.2 | **Library → Settings 导航** | P0 | 点击设置按钮 | 导航到 Settings 页面，slideRight 动画 | 正常导航 |
| 6.3 | **Settings → Privacy Policy 导航** | P1 | 点击隐私政策 | 导航到 PrivacyPolicy 页面 | 正常导航 |
| 6.4 | **Settings → User Agreement 导航** | P1 | 点击用户协议 | 导航到 UserAgreement 页面 | 正常导航 |
| 6.5 | **Settings → Feedback 导航** | P1 | 点击反馈 | 导航到 Feedback 页面 | 正常导航 |
| 6.6 | **Library → Editor 导航** | P0 | 点击图片 | 导航到 Editor 页面，slideRight 动画 | 核心路径 |
| 6.7 | **Editor → PresetsDiscovery 导航** | P1 | 点击预设 | 导航到 PresetsDiscovery，slideUp 动画 | 类型安全返回 |
| 6.8 | **PresetsDiscovery → Editor 返回结果** | P1 | 选择预设 → 返回 | 通过 SavedStateHandle 传递 Preset 对象 | ResultKeys.SELECTED_PRESET |
| 6.9 | **Editor → AiInpaint 导航** | P1 | 点击 AI 消除 | 导航到 AiInpaint，slideUp 动画 | 类型安全返回 |
| 6.10 | **AiInpaint → Editor 返回结果** | P1 | 完成消除 → 返回 | 通过 SavedStateHandle 传递 Bitmap 结果 | ResultKeys.AI_INPAINT_RESULT |
| 6.11 | **Editor → PresetImport 导航** | P1 | 导入预设 | 导航到 PresetImport，slideUp 动画 | 类型安全返回 |
| 6.12 | **Library → ExportQueue 导航** | P1 | 点击导出队列 | 导航到 ExportQueue，slideUp 动画 | 底部弹窗 |
| 6.13 | **Library → LUT Market 导航** | P2 | 点击 LUT 市场 | 导航到 LutMarket，slideUp 动画 | 底部弹窗 |
| 6.14 | **Library → Recipe Share 导航** | P2 | 点击分享配方 | 导航到 RecipeShare，slideUp 动画 | 底部弹窗 |
| 6.15 | **Library → DAM Projects 导航** | P1 | 点击项目 | 导航到 DamProject，slideRight 动画 | DAM 功能 |
| 6.16 | **DAM Projects → Project Detail 导航** | P1 | 点击项目详情 | 导航到 DamProjectDetail，参数 projectId 传递 | 参数传递 |
| 6.17 | **Library → ComfyUI 导航** | P2 | 点击 AI 工作流 | 导航到 ComfyUi，slideUp 动画 | v1.10.4 新增 |
| 6.18 | **Library → Help 导航** | P2 | 点击帮助 | 导航到 HelpCenter，slideRight 动画 | v1.7.0 新增 |
| 6.19 | **深层链接快速跳转** | P0 | rapidraw://editor/xxx | 直接导航到 Editor，不经过 Library | deepLinks 支持 |
| 6.20 | **导航兼容层 Holder 兼容** | P1 | 验证 SelectedPresetHolder / AiInpaintResultHolder | 旧版调用方仍能正常工作 | deprecated 兼容层 |

---

## 7. 崩溃恢复与诊断

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 7.1 | **CrashHandler 捕获 Java 异常** | P0 | 抛 RuntimeException | 异常被捕获，写入本地日志文件，委托给系统 handler | 核心功能 |
| 7.2 | **CrashHandler 捕获 OOM** | P0 | 分配超大 Bitmap 触发 OOM | 识别为 CrashType.OOM，写入日志并上报 CrashReporter | 专用类型 |
| 7.3 | **CrashHandler 委托给系统 Handler** | P0 | 异常后验证系统对话框 | 系统崩溃对话框正常弹出 | 保留 Android 原生行为 |
| 7.4 | **CrashHandler 系统 Handler 自身崩溃兜底** | P1 | 模拟系统 Handler 崩溃 | killProcess + System.exit(10) | 避免卡死 |
| 7.5 | **CrashHandler 日志写入失败不崩溃** | P1 | 模拟磁盘满 | 不抛异常，日志记录 "Failed to persist crash" | 容错 |
| 7.6 | **CrashHandler 日志文件最大 20 个** | P1 | 写入 30 次崩溃日志 | 最旧 10 个被删除，保留最新 20 个 | MAX_LOG_FILES=20 |
| 7.7 | **CrashHandler PII 脱敏** | P0 | 验证崩溃日志内容 | 日志中路径/账户名/email/电话/IP 被替换为占位符 | sanitizePii |
| 7.8 | **CrashHandler 协程异常处理器** | P0 | 协程中抛异常 | 异常被捕获，写入日志，不闪退 | coroutineExceptionHandler |
| 7.9 | **NativeCrashHandler 安装** | P0 | 验证 native 层信号处理器 | SIGSEGV/SIGABRT 被捕获，写入日志 | native_crash_handler.cpp |
| 7.10 | **NativeCrashHandler 不可用回退** | P1 | 模拟 native 库缺失 | NativeCrashHandler.installFallback 执行 | Java 层兜底 |
| 7.11 | **CrashReporter 异步上报** | P0 | 崩溃触发上报 | 崩溃信息异步上报到远程服务，不阻塞主线程 | CrashReporter.report |
| 7.12 | **CrashReporter CrashDeduplicator 去重** | P1 | 同一异常 5 分钟内多次触发 | 只上报一次 | 去重机制 |
| 7.13 | **CrashStorage 本地持久化** | P1 | 崩溃后重启 | 上次崩溃日志可从本地读取 | CrashStorage 持久化 |
| 7.14 | **CrashHandler 版本信息记录** | P1 | 验证崩溃日志中版本信息 | appVersionName 和 appVersionCode 正确 | 诊断用途 |
| 7.15 | **CrashHandler 设备信息记录** | P1 | 验证崩溃日志中设备信息 | Manufacturer/Model/Brand/Android/ABI 正确 | 诊断用途 |
| 7.16 | **CrashHandler 崩溃日志目录创建** | P1 | 删除目录后调用 | 目录自动创建 | crashLogDir |
| 7.17 | **StartupRecovery 连续崩溃 3 次触发恢复** | P0 | 模拟连续 3 次启动崩溃 | 第 4 次启动时 cacheDir 全部清理 + 白名单外 prefs 删除 | 核心恢复 |
| 7.18 | **StartupRecovery 白名单 prefs 保护** | P0 | 恢复后检查 | rapidraw_startup/rapidraw_onboarding/permission_history/rapidraw_pending_uri 四个 prefs 保留 | 防止误删 |
| 7.19 | **StartupRecovery 恢复中异常容错** | P1 | 模拟 IO 异常 | runCatching 包裹，不导致二次崩溃 | 自恢复设计 |
| 7.20 | **StartupRecovery 计数器正确递增** | P0 | 每次启动递增 | 1 → 2 → 3 → 0(恢复) | 数字逻辑正确 |
| 7.21 | **StartupRecovery 成功后清零** | P0 | 正常启动完成后 | counter=0 | onStartupSuccess |
| 7.22 | **StartupRecovery 恢复日志** | P1 | 验证恢复日志 | Log.w "Detected N consecutive startup crashes, attempting recovery" | 诊断 |
| 7.23 | **CrashHandler appVersionNameStatic** | P1 | 验证静态方法 | 返回正确的版本号字符串 | 供 CrashReporter 和 ANRWatchdog 使用 |
| 7.24 | **CrashHandler crashLogDirStatic 动态路径** | P1 | dev/staging 包名变体验证 | 日志写入正确目录 | 2026.07 hotfix: 修复硬编码路径 |
| 7.25 | **CrashHandler writeCrashToFileStatic** | P1 | ANRWatchdog 调用验证 | ANR 日志正确写入 | 静态方法暴露 |
| 7.26 | **CrashReporter.shutdown** | P1 | Application.onTerminate 中调用 | 上报队列排空，连接关闭 | 资源释放 |
| 7.27 | **CrashReporter OOM 类型区分** | P1 | 验证 OOM 上报 | CrashType.OOM 单独标记 | 用于分析 |
| 7.28 | **CrashReporter Coroutine 类型区分** | P1 | 验证协程异常上报 | CrashType.COROUTINE 单独标记 | 用于分析 |
| 7.29 | **CrashReporter ANR 上报** | P1 | 通过 ANRWatchdog 触发 | CrashReporter.reportAnr 成功 | ANR 去重 |
| 7.30 | **CrashReporter 无网络时本地缓存** | P1 | 断网 → 崩溃 | 崩溃日志本地缓存，联网后补报 | 离线缓存 |

---

## 8. ANR / 死锁检测

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 8.1 | **ANRWatchdog 正常启动** | P0 | 启动后验证监控线程 | 守护线程 running，优先级 MAX | 2s 阈值，1s 间隔 |
| 8.2 | **ANRWatchdog 重复启动防护** | P1 | 调用 start() 两次 | 第二次打印 WARN，不重复创建线程 | AtomicBoolean 防重入 |
| 8.3 | **ANRWatchdog 检测主线程卡顿** | P0 | 主线程 sleep 3s | 检测到卡顿，dump 主线程堆栈，写入日志 | 超出 threshold |
| 8.4 | **ANRWatchdog 卡顿恢复后继续监控** | P1 | 短暂卡顿后恢复 | 恢复后继续正常监控，不停机 | 连续监控 |
| 8.5 | **ANRWatchdog 去重 5 分钟** | P1 | 同一卡顿根因重复触发 | 5 分钟内同堆栈不上报 | DUPLICATE_SUPPRESS_MS=300000 |
| 8.6 | **ANRWatchdog 卡顿写入 CrashLog** | P1 | 验证 ANR 日志文件 | crash_anr_*.log 文件存在，内容正确 | CrashHandler.writeCrashToFileStatic |
| 8.7 | **ANRWatchdog CrashReporter 上报** | P1 | 验证 ANR 上报 | CrashReporter.reportAnr 调用 | ANR 专用上报 |
| 8.8 | **ANRWatchdog 停止监控** | P1 | 调用 stop() | 监控线程 interrupt，running=false | 资源释放 |
| 8.9 | **ANRWatchdog elapsedRealtime 时间基准** | P1 | 模拟系统时间跳变 | ANR 检测不受系统时间影响 | 2026.07 hotfix |
| 8.10 | **DeadlockDetector 正常启动** | P0 | 启动后验证检测线程 | 守护线程 running，阈值 5s，间隔 2s | 死锁检测 |
| 8.11 | **DeadlockDetector 重复启动防护** | P1 | 调用 start() 两次 | 第二次打印 WARN，不重复创建线程 | AtomicBoolean 防重入 |
| 8.12 | **DeadlockDetector 检测死锁** | P0 | 模拟主线程卡死 6s | 检测到死锁，收集所有线程堆栈，上报 CrashReporter | 超过 thresholdMs |
| 8.13 | **DeadlockDetector 收集所有线程堆栈** | P1 | 验证死锁日志 | 所有线程名、状态、优先级、守护线程标志、堆栈正确 | 完整信息 |
| 8.14 | **DeadlockDetector onDeadlockDetected 回调** | P1 | 注册回调 | 死锁时回调被调用 | 自定义处理 |
| 8.15 | **DeadlockDetector checkMainThread 主动检查** | P1 | 调用 checkMainThread(3000) | 返回 true 表示主线程正常 | 手动检查 |
| 8.16 | **DeadlockDetector 停止检测** | P1 | 调用 stop() | 检测线程 interrupt，running=false | 资源释放 |
| 8.17 | **DeadlockDetector 锁顺序文档化** | P2 | 验证锁获取顺序 | bitmapMutex → gpuMutex → BranchableHistory.lock → ThumbnailDiskCache.lock → CrashReporter.crashCountWindow | 代码审查 |
| 8.18 | **ANRWatchdog 与 DeadlockDetector 共存** | P1 | 同时运行 | 不互相干扰，两个检测线程独立运行 | 协同检测 |
| 8.19 | **ANRWatchdog 在 Application.onTerminate 中停止** | P1 | 验证 onTerminate | ANRWatchdog.stop() 被调用 | 资源释放 |
| 8.20 | **DeadlockDetector 在 Application.onTerminate 中停止** | P1 | 验证 onTerminate | DeadlockDetector.stop() 被调用 | 资源释放 |

---

## 9. 进程死亡与恢复

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 9.1 | **进程死亡后 Activity 恢复** | P0 | adb shell kill 进程 → 从最近任务恢复 | Activity 重建，savedInstanceState 恢复 | 系统行为 |
| 9.2 | **进程死亡后 pendingImageUri 恢复** | P0 | 进程死亡前有外部 Intent → 恢复 | 从 SafePreferences 恢复 URI，LaunchedEffect 导航到 Editor | 2026.07 新增 |
| 9.3 | **进程死亡后 Onboarding 状态保持** | P0 | 引导完成后进程死亡 → 恢复 | 直接进入 Library，不重看引导 | OnboardingState 持久化 |
| 9.4 | **进程死亡后权限历史保持** | P1 | 恢复后检查 permission_history | 权限请求历史记录保持 | 白名单 prefs |
| 9.5 | **进程死亡后 Editor 状态恢复** | P1 | 编辑中进程死亡 | EditorViewModel 通过 SavedStateHandle 恢复状态 | 编辑状态保持 |
| 9.6 | **进程死亡后导航栈恢复** | P1 | 多层导航中进程死亡 | 恢复后导航栈正确重建 | Navigation Compose 支持 |
| 9.7 | **进程死亡后数据库状态恢复** | P1 | 数据库操作中进程死亡 | Room 数据库完整性保持，WAL 恢复 | WAL 模式 |
| 9.8 | **进程死亡后 WorkManager 任务恢复** | P1 | 导出队列中进程死亡 | WorkManager 恢复未完成的任务 | WorkManager 恢复 |
| 9.9 | **进程死亡时 onSaveInstanceState 保存** | P0 | 验证 Activity 保存状态 | 关键状态写入 Bundle | Activity 生命周期 |
| 9.10 | **进程死亡时 ViewModel 销毁** | P1 | 验证 onCleared 调用 | ViewModel 清理资源 | 资源释放 |
| 9.11 | **进程死亡恢复后 BillingManager 重建** | P1 | 恢复后验证购买状态 | BillingManager 重新连接，购买状态恢复 | 持久化 |
| 9.12 | **进程死亡恢复后 CrashHandler 重建** | P1 | 恢复后验证异常捕获 | CrashHandler 重新安装，异常可正常捕获 | Application.onCreate |
| 9.13 | **进程死亡恢复后 ANRWatchdog 重建** | P1 | 恢复后验证 ANR 监控 | ANRWatchdog 重新启动，监控正常 | Application.onCreate |
| 9.14 | **进程死亡恢复后 PerformanceMonitor 重建** | P1 | 恢复后验证性能监控 | PerformanceMonitor 重新初始化 | Application.onCreate |
| 9.15 | **进程死亡后 onTrimMemory 清理** | P1 | 系统低内存 → 进程死亡 → 恢复 | 缓存文件被清理，不残留损坏文件 | 内存管理 |

---

## 10. 兼容性矩阵

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 10.1 | **Android 8.0 (API 26) 最低版本** | P0 | API 26 模拟器/设备 | 基本功能正常，无 API 26+ 特有调用崩溃 | minSdk=26 |
| 10.2 | **Android 9 (API 28)** | P0 | API 28 设备 | 功能正常，App Standby Buckets 检测 | P 引入 |
| 10.3 | **Android 10 (API 29)** | P0 | API 29 设备 | 分区存储正常，后台启动限制处理 | Q 引入 |
| 10.4 | **Android 11 (API 30)** | P0 | API 30 设备 | 沉浸式模式正常，WindowInsetsController 使用 | R 引入 |
| 10.5 | **Android 12 (API 31)** | P0 | API 31 设备 | Material You 动态取色，无异常 | S 引入 |
| 10.6 | **Android 13 (API 33)** | P0 | API 33 设备 | 通知权限请求，每应用语言，READ_MEDIA_IMAGES | Tiramisu 引入 |
| 10.7 | **Android 14 (API 34)** | P0 | API 34 设备 | 预测性返回，前台服务类型，Ultra HDR | UpsideDownCake 引入 |
| 10.8 | **Android 15 (API 35)** | P0 | API 35 设备 | 16KB 页面大小，edge-to-edge 默认 | VanillaIceCream 引入 |
| 10.9 | **Android 16 (API 36)** | P0 | Pixel 8+ API 36 预览 | 所有新特性验证，16KB 页面兼容 | 2026 目标 |
| 10.10 | **Google Pixel 8/9** | P0 | Pixel 真机 | 完整功能，16KB 页面大小，edge-to-edge | 标杆设备 |
| 10.11 | **Samsung Galaxy S24/S25** | P0 | Samsung 真机 | OneUI 兼容，省电模式不干扰导出 | 三星市场份额 |
| 10.12 | **Xiaomi 14/15** | P0 | Xiaomi 真机 | MIUI/HyperOS 兼容，自启动设置可达 | 小米市场份额 |
| 10.13 | **OPPO Find X8/X9** | P0 | OPPO 真机 | ColorOS 兼容，增强触觉反馈，高分辨率预览 | 核心优化目标 |
| 10.14 | **Huawei P60/P70** | P1 | Huawei 真机 | EMUI/HarmonyOS 兼容，启动管理 | 华为市场 |
| 10.15 | **Vivo X100/X200** | P1 | Vivo 真机 | Funtouch OS/OriginOS 兼容，后台限制 | Vivo 市场 |
| 10.16 | **OnePlus 12/13** | P1 | OnePlus 真机 | OxygenOS 兼容，后台冻结 | 一加市场 |
| 10.17 | **Motorola razr 折叠屏** | P1 | Motorola 真机 | 折叠屏适配，屏幕旋转 | 折叠屏 |
| 10.18 | **Samsung Galaxy Z Fold/Flip** | P1 | Samsung 折叠屏 | 折叠屏适配，多窗口 | 折叠屏 |
| 10.19 | **ASUS ROG Phone** | P2 | ASUS 真机 | 游戏手机兼容，高性能模式 | 小众设备 |
| 10.20 | **Sony Xperia** | P2 | Sony 真机 | 原生 Android 兼容 | 小众设备 |
| 10.21 | **Nothing Phone** | P2 | Nothing 真机 | 原生 Android 兼容 | 小众设备 |
| 10.22 | **x86_64 模拟器 (Android Emulator)** | P0 | CI 测试 | 基本功能正常，native 库 Houdini 翻译 | CI 环境 |
| 10.23 | **ARM 模拟器** | P1 | ARM 模拟器 | 功能完整，native 库原生运行 | 测试环境 |
| 10.24 | **低内存设备 (4GB RAM)** | P1 | DeviceTier.MID 设备 | 功能降级，CPU 管线，瓦片 1024 | 自适应优化 |
| 10.25 | **极低内存设备 (2GB RAM)** | P2 | DeviceTier.LOW 设备 | 功能降级，CPU 管线，瓦片 512 | 极限情况 |
| 10.26 | **平板设备 (Samsung Tab)** | P1 | 平板设备 | 布局自适应，大屏优化 | 平板 |
| 10.27 | **Chromebook (Android 容器)** | P2 | Chromebook | 基本功能正常，键盘/鼠标适配 | 桌面端 |
| 10.28 | **SystemCompatibility 报告各版本验证** | P1 | 各 API 级别设备 | isApiCompliant / isGooglePlayCompliant / supportedFeatures / unsupportedFeatures 正确 | 兼容性报告 |
| 10.29 | **DeviceOptimizer.getDeviceTier 分级** | P1 | 不同 RAM/CPU 设备 | 正确分级 LOW/MID/HIGH/FLAGSHIP | 自适应 |
| 10.30 | **OemCompatibility 各 OEM 检测** | P1 | 各 OEM 设备 | OemType 正确识别 | 兼容性适配 |

---

## 11. 性能与启动指标

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 11.1 | **冷启动时间 (TTID)** | P0 | Systrace/Perfetto | 旗舰设备 < 500ms，中端设备 < 800ms | Baseline Profile 优化 |
| 11.2 | **热启动时间** | P0 | 从最近任务恢复 | < 200ms | 进程存活 |
| 11.3 | **温启动时间** | P1 | 进程被杀死但 Activity 在后台 | < 400ms | 进程重建 |
| 11.4 | **首帧渲染时间 (TTFR)** | P0 | 启动到首帧 Compose 渲染 | < 300ms (旗舰) | Composable 首帧 |
| 11.5 | **StartupOptimizer CRITICAL 阶段耗时** | P0 | 日志记录 | < 100ms | 8 个 CRITICAL 任务 |
| 11.6 | **StartupOptimizer HIGH 阶段耗时** | P1 | 日志记录 | IdleHandler 执行，不阻塞首帧 | 非阻塞 |
| 11.7 | **Baseline Profile 预编译效果** | P0 | 对比有无 Profile 的启动时间 | 有 Profile 时启动时间减少 20-30% | AOT 编译 |
| 11.8 | **Baseline Profile 内容覆盖** | P1 | 验证 Profile 中关键方法 | RapidRawApp.onCreate/MainActivity.onCreate/Compose Theme/Navigation 均覆盖 | 代码审查 |
| 11.9 | **PerformanceMonitor 帧率监控** | P1 | 滑动图库/编辑操作 | FPS ≥ 55 (旗舰)，FPS ≥ 30 (中端) | JankStats |
| 11.10 | **PerformanceMonitor P95 帧时间** | P1 | 关键操作帧数据 | P95 < 16ms (旗舰) | 帧数据分析 |
| 11.11 | **PerformanceMonitor 丢帧率** | P1 | 连续 100 帧 | 丢帧率 < 5% | 性能回归检测 |
| 11.12 | **PerformanceMonitor 性能回归自动降级** | P1 | 丢帧率 > 10% | onJankDegrade 回调触发 | 自动降级 |
| 11.13 | **PerformanceMonitor 热降频响应** | P1 | 设备发热 | ThermalLevel 变化，MODERATE+ 触发降级 | 热降频适配 |
| 11.14 | **PerformanceMonitor 6 级热状态** | P1 | 验证热状态映射 | NONE→LIGHT→MODERATE→SEVERE→CRITICAL→EMERGENCY 正确 | PowerManager 状态 |
| 11.15 | **PerformanceMonitor.snapshot 性能快照** | P2 | 导出性能报告 | PerformanceReport 数据完整 | 用于 CI 分析 |
| 11.16 | **PerformanceMonitor 内存在合理范围** | P1 | 启动后 5 分钟内存占用 | < 300MB (空闲)，< 800MB (编辑大图) | 内存监控 |
| 11.17 | **DeviceOptimizer 推荐线程数** | P1 | 不同设备验证 | 旗舰 8 线程，中端 4 线程 | 自适应 |
| 11.18 | **DeviceOptimizer GPU 管线决策** | P1 | HIGH/FLAGSHIP 用 GPU，LOW/MID 用 CPU | 正确选择管线 | shouldUseGpuPipeline |
| 11.19 | **DeviceOptimizer 预览分辨率** | P1 | OPPO 高端 2048，其他 1536 | 预览分辨率正确 | 设备自适应 |
| 11.20 | **冷启动 CPU 使用率** | P1 | 启动时 CPU profiling | 无 CPU 尖峰超过 1s | 性能优化 |

---

## 12. 安全与完整性

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 12.1 | **SecurityProvider 签名校验** | P0 | 启动时验证 | 签名 SHA-256 正确，verifyAppSignature 返回 true | 防篡改 |
| 12.2 | **SecurityProvider 预期指纹校验** | P1 | 传入 expectedFingerprint | 签名匹配返回 true，不匹配返回 false | 签名固定 |
| 12.3 | **SecurityProvider 调试器检测** | P1 | 连接调试器 | isDebuggerAttached 返回 true | 开发环境 |
| 12.4 | **SecurityProvider 模拟器检测** | P1 | 模拟器运行 | isEmulator 返回 true | 确认在模拟器 |
| 12.5 | **SecurityProvider ROOT 检测** | P1 | ROOT 设备 | isDeviceRooted 返回 true | 设备安全性 |
| 12.6 | **SecurityProvider 安全随机数** | P1 | 验证 randomBytes/randomHex/randomBase64 | 使用 SecureRandom，非 Random | 加密安全 |
| 12.7 | **SecurityProvider SHA-256 哈希** | P1 | 验证 sha256/sha256Hex | 结果与标准 SHA-256 一致 | 哈希工具 |
| 12.8 | **SecurityProvider HMAC-SHA256** | P1 | 验证 hmacSha256 | 结果与标准 HMAC-SHA256 一致 | 签名工具 |
| 12.9 | **SecurityProvider SSL 证书固定** | P1 | 验证 createPinnedSslContext | 不匹配的证书抛 SSLPeerUnverifiedException | 证书固定 |
| 12.10 | **SecurityProvider 安全字符串检查** | P2 | 验证 isSafeString | 包含危险字符返回 false | 输入验证 |
| 12.11 | **PlayIntegrityHelper 完整性检查** | P0 | 启动后 5s 执行 | Verdict.PASS 或 BASIC_ONLY | 本地启发式 |
| 12.12 | **PlayIntegrityHelper 调试器检测** | P1 | 连接调试器 | Verdict.FAIL, errorMessage="Debugger attached" | 安全检测 |
| 12.13 | **PlayIntegrityHelper 模拟器检测** | P1 | 模拟器运行 | Verdict.BASIC_ONLY | 降级 |
| 12.14 | **PlayIntegrityHelper ROOT 检测** | P1 | ROOT 设备 | Verdict.BASIC_ONLY | 降级 |
| 12.15 | **PlayIntegrityHelper 缓存 5 分钟** | P1 | 短时间内多次调用 | 返回缓存结果，不重复检查 | CACHE_DURATION_MS |
| 12.16 | **PlayIntegrityHelper 高级功能控制** | P1 | 验证 isPremiumFeatureAllowed | 仅 PASS 时允许高级功能 | 功能门控 |
| 12.17 | **EncryptedPreferences 加密存储** | P1 | 验证敏感数据加密 | 使用 Android Keystore 加密，数据不可直接读取 | 安全存储 |
| 12.18 | **PermissionValidator 权限验证** | P1 | 验证权限检查 | 所有权限检查正确 | 安全组件 |
| 12.19 | **network_security_config 配置** | P1 | 验证 HTTPS 连接 | 不允许 cleartext 流量（release） | 网络安全 |
| 12.20 | **ProGuard PII 保护** | P1 | 反编译 release APK | 日志调用被移除，敏感信息不泄露 | -assumenosideeffects |

---

## 13. 构建变体与签名

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 13.1 | **dev 变体构建** | P0 | ./gradlew assembleDevDebug | 构建成功，applicationId=com.rapidraw.dev | 开发环境 |
| 13.2 | **staging 变体构建** | P0 | ./gradlew assembleStagingDebug | 构建成功，applicationId=com.rapidraw.staging | 预发布环境 |
| 13.3 | **prod 变体构建** | P0 | ./gradlew assembleProdRelease | 构建成功，applicationId=com.rapidraw | 生产环境 |
| 13.4 | **dev 变体功能** | P1 | 安装 dev 变体 | ENABLE_DEBUG_TOOLS=true，API_BASE_URL=dev-api | 调试工具启用 |
| 13.5 | **staging 变体功能** | P1 | 安装 staging 变体 | ENABLE_DEBUG_TOOLS=false，API_BASE_URL=staging-api | 预发布验证 |
| 13.6 | **prod 变体功能** | P1 | 安装 prod 变体 | ENABLE_DEBUG_TOOLS=false，API_BASE_URL=api | 生产验证 |
| 13.7 | **dev 变体包名** | P1 | 验证 applicationId | com.rapidraw.dev，与 prod 不冲突 | 可同时安装 |
| 13.8 | **dev 变体应用名** | P1 | 验证字符串资源 | "RapidRAW Dev" | 区分 |
| 13.9 | **staging 变体应用名** | P1 | 验证字符串资源 | "RapidRAW Staging" | 区分 |
| 13.10 | **prod 变体签名** | P0 | 验证签名配置 | 使用 release.keystore 或 CI 环境变量签名 | 正式签名 |
| 13.11 | **CI 环境 KEYSTORE_BASE64 签名** | P0 | CI 构建验证 | 从环境变量解码 keystore 成功 | CI 安全 |
| 13.12 | **CI 环境 KEYSTORE_PASSWORD 缺失** | P1 | 模拟 CI 环境变量缺失 | GradleException 提示缺失环境变量 | 错误提示 |
| 13.13 | **本地 release.keystore 签名** | P1 | 本地构建 release | 使用 app/release.keystore 签名 | 本地开发 |
| 13.14 | **无签名配置 release 构建** | P1 | 无 keystore 无环境变量 | 不签名但可构建（仅用于测试） | 测试用途 |
| 13.15 | **R8 混淆启用** | P0 | release 构建验证 | isMinifyEnabled=true, isShrinkResources=true | 代码混淆 |
| 13.16 | **R8 禁用 (disableR8=true)** | P1 | -PdisableR8=true 构建 | isMinifyEnabled=false | CI 沙箱模式 |
| 13.17 | **ABI 过滤 arm64-v8a** | P0 | 检查 release APK lib 目录 | 只包含 arm64-v8a 的 .so 文件 | 默认行为 |
| 13.18 | **ABI 过滤 armeabi-v7a** | P1 | -PincludeArmV7=true 构建 | 包含 armeabi-v7a 的 .so 文件 | 可选 |
| 13.19 | **ABI 过滤 x86_64** | P1 | -PincludeX86_64=true 构建 | 包含 x86_64 的 .so 文件 | 模拟器测试 |
| 13.20 | **资源过滤 zh/en/ja/ko** | P1 | 检查 APK 资源 | 仅包含 zh/en/ja/ko 四种语言资源 | 减小 APK 体积 |

---

## 14. ProGuard/R8 混淆

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 14.1 | **R8 混淆后应用启动** | P0 | release 构建启动 | 不崩溃，所有功能正常 | 核心验证 |
| 14.2 | **Room 数据库类保留** | P0 | release 构建验证数据库 | CRUD 操作正常，@Entity/@Dao 类不被混淆 | -keep 规则 |
| 14.3 | **kotlinx.serialization 保留** | P0 | release 构建验证序列化 | 序列化/反序列化正常，Companion 不被混淆 | -keep 规则 |
| 14.4 | **Compose UI 类保留** | P0 | release 构建验证 UI | UI 渲染正常，@Composable 函数不被混淆 | -keep 规则 |
| 14.5 | **Navigation 类保留** | P0 | release 构建验证导航 | 导航正常，路由参数不丢失 | -keep 规则 |
| 14.6 | **ViewModel 工厂保留** | P0 | release 构建验证 ViewModel | EditorViewModel.Factory 不被混淆 | -keep 规则 |
| 14.7 | **GPU/OpenGL 类保留** | P0 | release 构建验证 GPU 管线 | GPU 着色器正常，GpuPipeline 不被混淆 | -keep 规则 |
| 14.8 | **JNI/Native 桥接保留** | P0 | release 构建验证 native 调用 | RawDecoder native 方法不被混淆 | -keep 规则 |
| 14.9 | **CrashHandler 保留** | P0 | release 构建验证崩溃捕获 | 崩溃日志正确写入，类名不混淆 | -keep 规则 |
| 14.10 | **TensorFlow Lite 保留** | P1 | release 构建验证 AI 推理 | AI 功能正常 | -keep 规则 |
| 14.11 | **Raw 资源保留** | P0 | release 构建验证 GPU 着色器 | gpu_*.frag 和 gpu_*.vert 文件不被删除 | -keepresources raw/** |
| 14.12 | **R 类资源字段保留** | P1 | release 构建验证反射 | R$font/R$drawable 字段可反射访问 | -keep 规则 |
| 14.13 | **安全模块保留** | P1 | release 构建验证签名校验 | SecurityProvider 功能正常 | -keep 规则 |
| 14.14 | **OEM 兼容模块保留** | P1 | release 构建验证 OEM 检测 | OemCompatibility/BackgroundCompatibility 功能正常 | -keep 规则 |
| 14.15 | **SourceFile/LineNumberTable 保留** | P1 | release 构建验证崩溃堆栈 | 崩溃日志包含行号信息 | -keepattributes |
| 14.16 | **Log.v/d/i 移除** | P1 | release 构建验证日志 | 敏感日志被移除，Log.w/e 保留 | -assumenosideeffects |
| 14.17 | **renamesourcefileattribute** | P1 | release 构建验证源码文件名 | 栈帧中 SourceFile 统一 | -renamesourcefileattribute |
| 14.18 | **Coroutines 保留** | P1 | release 构建验证协程 | MainDispatcherFactory/CoroutineExceptionHandler 正确 | -keep 规则 |
| 14.19 | **WorkManager 导出任务保留** | P1 | release 构建验证导出 | ExportWorker 正常执行 | -keep 规则 |
| 14.20 | **Cloud Sync 后端保留** | P1 | release 构建验证云同步 | CloudSync 功能正常 | -keep 规则 |

---

## 15. Native 库加载

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 15.1 | **LibRaw native 库加载** | P0 | 启动时验证 System.loadLibrary | libraw decoder 正常加载，无 UnsatisfiedLinkError | 核心 RAW 解码 |
| 15.2 | **RawSpeed3 native 库加载** | P0 | 启动时验证 | rawspeed3_capi 正常加载 | 高速 RAW 解码 |
| 15.3 | **Vulkan Compute native 库加载** | P1 | 启动时验证 | vulkan_compute 库正常加载 | GPU 加速 |
| 15.4 | **NativeCrashHandler native 库加载** | P0 | 启动时验证 | native_crash_handler 库正常加载 | 原生崩溃捕获 |
| 15.5 | **Native 库缺失回退** | P1 | 模拟 native 库不存在 | 功能降级但不崩溃，日志 WARN | 容错 |
| 15.6 | **Native 库版本不匹配** | P1 | 不匹配的 .so 替换 | 异常处理，应用不崩溃 | 容错 |
| 15.7 | **16KB 页面大小对齐** | P0 | API 35+ 设备，jniLibs.useLegacyPackaging=false | native 库以 16KB 对齐，正常加载 | 2025 年 Google Play 要求 |
| 15.8 | **CMake 构建配置** | P1 | 验证 CMakeLists.txt | cppFlags=-O2, 版本 3.22.1 | 构建优化 |
| 15.9 | **NDK 版本 26.3.11579264** | P1 | 验证 ndkVersion | 指定版本正确，兼容 16KB 页面 | 稳定版本 |
| 15.10 | **Native 库 ABI 兼容** | P1 | arm64-v8a/armeabi-v7a/x86_64 各平台 | 各 ABI 版本 native 库正常加载 | 多 ABI |
| 15.11 | **Native 库内存管理** | P1 | 解码大 RAW 文件 | 无 native 内存泄漏 | 内存监控 |
| 15.12 | **Native 库线程安全** | P1 | 多线程并发解码 | 无 SIGSEGV 崩溃 | 线程安全 |
| 15.13 | **Native 库兼容 Android 各版本** | P1 | API 26 - 36 各版本 | native 库在各版本正常加载 | 版本兼容 |
| 15.14 | **Native 库 OEM 兼容** | P1 | 各 OEM 设备 | 所有 OEM 设备 native 库正常加载 | OEM 兼容 |
| 15.15 | **Native 库加载性能** | P2 | 测量加载时间 | 加载时间 < 100ms | 性能指标 |

---

## 16. 通知渠道

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 16.1 | **通知渠道创建** | P0 | 启动后验证系统设置 | 3 个渠道：export_progress/sync/update | Android 8.0+ |
| 16.2 | **导出进度渠道属性** | P1 | 验证渠道设置 | IMPORTANCE_HIGH, showBadge=true, vibration=true | 用户感知 |
| 16.3 | **云端同步渠道属性** | P1 | 验证渠道设置 | IMPORTANCE_DEFAULT, showBadge=false | 低打扰 |
| 16.4 | **应用更新渠道属性** | P1 | 验证渠道设置 | IMPORTANCE_DEFAULT, showBadge=false | 低打扰 |
| 16.5 | **通知渠道重复创建** | P1 | 多次调用 initialize | 不崩溃，系统自动忽略已存在的渠道 | 幂等 |
| 16.6 | **通知渠道在 API < 26 跳过** | P2 | minSdk 26，理论上不触发 | 不抛异常 | 防御性代码 |
| 16.7 | **通知渠道多语言** | P1 | 切换系统语言 | 渠道名称跟随系统语言 | 本地化 |
| 16.8 | **POST_NOTIFICATIONS 权限后渠道可用** | P0 | 授予通知权限 → 导出 | 导出进度通知正常显示 | 权限依赖 |
| 16.9 | **POST_NOTIFICATIONS 权限拒绝后渠道** | P1 | 拒绝通知权限 → 导出 | 导出完成但无通知，不崩溃 | 降级处理 |
| 16.10 | **NotificationChannels 在 CRITICAL 阶段初始化** | P1 | 验证初始化顺序 | 在 CrashHandler 之后，首帧之前 | 启动顺序 |

---

## 17. 主题与配置

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 17.1 | **RapidRawTheme 正确渲染** | P0 | 启动后验证 | 深色主题 (EditorBackground)，AMOLED 友好 | 3 色调暗色 |
| 17.2 | **HasselbladOrange 主色调** | P1 | 验证 UI 组件颜色 | 按钮/滑块/PageIndicator 使用 HasselbladOrange | 品牌色 |
| 17.3 | **深色模式适配** | P0 | 系统深色模式 | 所有页面深色主题正确 | Dark Theme |
| 17.4 | **动态取色 (Material You)** | P1 | Android 12+ 设备 | 壁纸颜色影响应用主题 | 系统级 |
| 17.5 | **字体缩放 1.3x 限制** | P0 | 系统超大字体 | UI 文字不溢出，不截断 | 无障碍 |
| 17.6 | **RTL 布局支持** | P1 | 阿拉伯语/希伯来语 | 布局正确翻转 | supportsRtl=true |
| 17.7 | **Motion 动画过渡** | P1 | 验证页面切换动画 | enterSlideRight/exitSlideLeft/enterSlideUp/exitSlideDown 流畅 | 动画效果 |
| 17.8 | **Color.kt 颜色定义完整** | P1 | 验证所有颜色引用 | 无未定义颜色引用 | 主题系统 |
| 17.9 | **Type.kt 字体定义完整** | P1 | 验证所有字体引用 | 无未定义字体引用 | 字体系统 |
| 17.10 | **Spacing.kt 间距定义** | P1 | 验证间距一致性 | 组件间距统一 | 设计系统 |
| 17.11 | **Shapes.kt 形状定义** | P1 | 验证圆角一致性 | 组件圆角统一 | 设计系统 |
| 17.12 | **edge-to-edge 系统栏适配** | P0 | 验证内容区域 | 内容不被系统栏遮挡 | enableEdgeToEdge |
| 17.13 | **imePadding 键盘适配** | P1 | 键盘弹出 | 内容正确上移，不被键盘遮挡 | imePadding() |
| 17.14 | **displayCutout 刘海屏适配** | P1 | 刘海屏/挖孔屏设备 | 内容不被刘海遮挡 | windowInsetsPadding(displayCutout) |
| 17.15 | **窗口大小类 (WindowSizeClass)** | P1 | 折叠屏/平板 | 布局自适应窗口大小变化 | material3.windowsize |

---

## 18. 外部 Intent 与深层链接

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 18.1 | **ACTION_VIEW image/* Intent** | P0 | 从图库/文件管理器打开图片 | 导航到 Editor，正确解码图片 | 核心 Intent |
| 18.2 | **ACTION_VIEW DNG 格式** | P0 | 打开 .dng 文件 | 正确识别并解码 DNG 格式 | MIME: image/x-adobe-dng |
| 18.3 | **ACTION_VIEW CR2 格式** | P0 | 打开 .cr2 文件 | 正确识别并解码 Canon CR2 | MIME: image/x-canon-cr2 |
| 18.4 | **ACTION_VIEW CR3 格式** | P0 | 打开 .cr3 文件 | 正确识别并解码 Canon CR3 | MIME: image/x-canon-cr3 |
| 18.5 | **ACTION_VIEW NEF 格式** | P0 | 打开 .nef 文件 | 正确识别并解码 Nikon NEF | MIME: image/x-nikon-nef |
| 18.6 | **ACTION_VIEW ARW 格式** | P0 | 打开 .arw 文件 | 正确识别并解码 Sony ARW | MIME: image/x-sony-arw |
| 18.7 | **ACTION_VIEW RAF 格式** | P0 | 打开 .raf 文件 | 正确识别并解码 Fuji RAF | MIME: image/x-fuji-raf |
| 18.8 | **ACTION_VIEW ORF 格式** | P0 | 打开 .orf 文件 | 正确识别并解码 Olympus ORF | MIME: image/x-olympus-orf |
| 18.9 | **ACTION_VIEW RW2 格式** | P0 | 打开 .rw2 文件 | 正确识别并解码 Panasonic RW2 | MIME: image/x-panasonic-rw2 |
| 18.10 | **ACTION_VIEW PEF 格式** | P0 | 打开 .pef 文件 | 正确识别并解码 Pentax PEF | MIME: image/x-pentax-pef |
| 18.11 | **ACTION_VIEW SRW 格式** | P0 | 打开 .srw 文件 | 正确识别并解码 Samsung SRW | MIME: image/x-samsung-srw |
| 18.12 | **ColorOS EDIT_IMAGE Intent** | P1 | OPPO 相册 → 编辑图片 | 正确接收并导航到 Editor | OPPO 专用 |
| 18.13 | **rapidraw://editor/{path} 深层链接** | P0 | 浏览器/外部应用测试 | 正确解析路径，导航到 Editor | 自定义 scheme |
| 18.14 | **rapidraw://editor_uri/{uri} 深层链接** | P0 | 浏览器/外部应用测试 | 正确解析 URI，导航到 Editor | 自定义 scheme |
| 18.15 | **深层链接 URI 编码** | P1 | 路径包含特殊字符 | URL encode/decode 正确，不崩溃 | 编码处理 |
| 18.16 | **深层链接空路径防御** | P1 | 传入空字符串 | 返回 Library，不崩溃 | 空值防御 |
| 18.17 | **深层链接非法 URI 防御** | P1 | 传入非法编码 | 返回 Library，不崩溃 | URL 解码异常 |
| 18.18 | **外部 Intent Onboarding 中延迟** | P0 | 引导未完成 → 外部 Intent | 缓存 URI，引导完成后导航 | 防止绕过引导 |
| 18.19 | **外部 Intent 隐私保护** | P1 | 验证 logcat | 不打印外部传入的 URI（防止泄露文件路径） | 安全隐私 |
| 18.20 | **外部 Intent 重复 URI 防护** | P1 | 同一图片多次发送 | 跳过重复导航 | 防止堆栈堆积 |

---

## 19. App Shortcuts

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 19.1 | **Shortcut 库图标** | P1 | 长按图标 → 选择快捷方式 | 显示 3 个快捷方式：library/recent_project/new_edit | App Shortcuts |
| 19.2 | **Shortcut "library" 导航** | P1 | 点击 library 快捷方式 | 导航到 Library 页面 | 正确导航 |
| 19.3 | **Shortcut "recent_project" 导航** | P1 | 点击 recent_project 快捷方式 | 导航到 DAM Projects 页面 | 正确导航 |
| 19.4 | **Shortcut "new_edit" 导航** | P1 | 点击 new_edit 快捷方式 | 导航到 Library 页面 | 正确导航 |
| 19.5 | **Shortcut 延迟 300ms 导航** | P1 | 验证 delay 等待 Compose | NavController 已就绪后再导航 | 防止 NavController 为 null |
| 19.6 | **Shortcut 图标资源** | P1 | 验证快捷方式图标 | ic_shortcut_edit/ic_shortcut_import/ic_shortcut_library 正确 | 视觉验证 |
| 19.7 | **Shortcut 多语言标签** | P1 | 切换系统语言 | 快捷方式标签跟随系统语言 | 本地化 |
| 19.8 | **Shortcut 在 Android 8.0+ 可用** | P1 | 验证 minSdk 26 | App Shortcuts 正常 | 系统支持 |
| 19.9 | **Shortcut 在 release 构建可用** | P1 | release 构建验证 | R8 混淆后快捷方式仍正常 | 混淆兼容 |
| 19.10 | **Shortcut 元数据声明** | P1 | 验证 Manifest | meta-data android:name="android.app.shortcuts" 正确 | @xml/shortcuts |

---

## 20. 内存与缓存

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 20.1 | **onTrimMemory RUNNING_MODERATE** | P1 | 模拟内存压力 | 提示清理缓存（日志记录） | 轻量清理 |
| 20.2 | **onTrimMemory BACKGROUND** | P1 | 应用进入后台 | 清理 raw_decode 缓存 + thumbnail 缓存 | 守护线程清理 |
| 20.3 | **onTrimMemory COMPLETE** | P1 | 系统严重内存不足 | 清理所有非必要缓存 | 降低被杀概率 |
| 20.4 | **cleanStaleDecodedRawCache 清理** | P1 | 缓存有 60s 以上的 raw_decode_ 文件 | 清理过期文件，日志记录释放大小 | 定时清理 |
| 20.5 | **cleanThumbnailDiskCache 清理** | P1 | 缓存有缩略图文件 | 清理所有缩略图，日志记录释放大小 | 可重新解码 |
| 20.6 | **largeHeap=true 内存申请** | P1 | 验证获取的堆内存 | 比默认堆更大 | RAW 处理需要 |
| 20.7 | **DeviceOptimizer 激进缓存策略** | P1 | OPPO 高端设备 (≥ 8GB) | shouldUseAggressiveCaching 返回 true | 旗舰设备 |
| 20.8 | **DeviceOptimizer 非 OPPO 设备缓存** | P1 | 非 OPPO 设备 | shouldUseAggressiveCaching 返回 false | 保守策略 |
| 20.9 | **DeviceOptimizer 可用内存检测** | P1 | 验证 getAvailableMemoryMb | 返回正确可用内存（非总内存） | 2026.07 hotfix |
| 20.10 | **ThumbnailDiskCache 磁盘缓存** | P1 | 验证缩略图读写 | 缓存命中/未命中正确 | 磁盘缓存 |
| 20.11 | **NetworkCache HTTP 缓存** | P1 | 验证 OkHttp 缓存 | 缓存目录创建，HTTP 响应缓存 | 网络缓存 |
| 20.12 | **Bitmap 回收** | P1 | 编辑大量图片 | 无内存泄漏，Bitmap.isRecycled 正确 | 内存管理 |
| 20.13 | **OOM 后降级处理** | P1 | 解码超大图片触发 OOM | 降级为较小尺寸，不崩溃 | 容错处理 |
| 20.14 | **SafePreferences 损坏恢复** | P1 | 模拟 SharedPreferences XML 损坏 | 自动删除损坏文件，重建 | 防止崩溃 |
| 20.15 | **SafePreferences 读取异常容错** | P1 | 模拟读取异常 | 返回默认值，不崩溃 | 所有读写方法 |

---

## 21. 数据库初始化

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 备注 |
|---|---------|--------|---------|---------|------|
| 21.1 | **RecipeDatabase 创建** | P0 | 首次启动 | 数据库正确创建，所有表存在 | Room 数据库 |
| 21.2 | **RecipeDatabase 迁移** | P0 | 数据库版本升级 | 迁移逻辑正确，数据不丢失 | 数据库迁移 |
| 21.3 | **RecipeDatabase 降级破坏性重建** | P1 | 降级安装 | fallbackToDestructiveMigration 触发 | 降级处理 |
| 21.4 | **RecipeDao CRUD 操作** | P1 | 验证增删改查 | 所有操作正常 | 数据访问 |
| 21.5 | **FavoriteDao CRUD 操作** | P1 | 验证收藏操作 | 所有操作正常 | 收藏功能 |
| 21.6 | **ProjectDao CRUD 操作** | P1 | 验证项目操作 | 所有操作正常 | 项目功能 |
| 21.7 | **RecipeEntity 序列化** | P1 | 验证 TypeConverters | RecipeConverters 正确转换 | 复杂类型 |
| 21.8 | **FavoriteEntity 序列化** | P1 | 验证 FavoriteConverters | 正确转换 | 复杂类型 |
| 21.9 | **数据库 WAL 模式** | P1 | 验证数据库日志模式 | WAL 模式启用，并发读写安全 | 性能优化 |
| 21.10 | **数据库文件损坏恢复** | P1 | 模拟数据库文件损坏 | 优雅降级，不崩溃 | 容错处理 |

---

## 22. 废弃归属与回归测试 (按热修复版本)

| # | 测试用例 | 优先级 | 测试方法 | 预期结果 | 回归归属 |
|---|---------|--------|---------|---------|---------|
| 22.1 | **POST_NOTIFICATIONS Manifest 声明** | P0 | 检查 Manifest | POST_NOTIFICATIONS 已声明 | v1.5.5 hotfix |
| 22.2 | **Onboarding 不再重复导航** | P0 | 快速双击"开始编辑" | 单次导航，不崩溃 | v1.5.5 hotfix |
| 22.3 | **Library 是已完成引导的起始页** | P0 | 已完成引导冷启动 | 直接进入 Library，不闪现引导 | v1.5.5 hotfix |
| 22.4 | **Editor 空路径防御** | P0 | 深层链接传入空路径 | 返回 Library，不崩溃 | v1.5.5 hotfix |
| 22.5 | **AiInpaint 空路径防御** | P0 | 空路径导航 | 返回上一页，不崩溃 | v1.5.5 hotfix |
| 22.6 | **CrashHandler 委托前检查 previous** | P1 | 验证 null 检查 | previous 为空时不崩溃 | v1.5.5 hotfix |
| 22.7 | **CrashHandler 写入失败不阻止委托** | P1 | 模拟磁盘满 | 委托给系统 handler 正常 | v1.5.5 hotfix |
| 22.8 | **Release 包崩溃日志也写入** | P1 | release 构建验证 | 协程异常日志写入文件 | v1.5.5 hotfix |
| 22.9 | **R8 不误删 CrashHandler catch 块** | P1 | 反编译验证 | catch 块完整保留 | v1.5.9 hotfix |
| 22.10 | **GPU 着色器资源保留** | P0 | release 构建验证 | gpu_*.frag/vert 文件存在 | v1.5.9 hotfix |
| 22.11 | **edge-to-edge try-catch 保护** | P1 | 模拟 enableEdgeToEdge 异常 | 失败不崩溃，仅日志 WARN | v1.6.3 |
| 22.12 | **Pre-onCreate 异常兜底** | P1 | 模拟 super.onCreate 前异常 | 异常被捕获，不闪退到桌面 | v1.6.3 |
| 22.13 | **ANRWatchdog 使用 elapsedRealtime** | P1 | 模拟系统时间跳变 | ANR 检测不受影响 | v1.10.6 hotfix |
| 22.14 | **StartupOptimizer 移除 ImageProcessor.init** | P1 | 编译验证 | 不报编译错误 | v1.10.6 hotfix |
| 22.15 | **CrashHandler crashLogDirStatic 动态路径** | P1 | dev/staging 变体验证 | 日志写入正确目录 | v1.10.6 hotfix |
| 22.16 | **pendingImageUriState mutableStateOf** | P0 | 外部 Intent 触发验证 | LaunchedEffect 观察到变化 | 2026.06 hotfix |
| 22.17 | **pendingImageUri 进程死亡恢复** | P0 | 模拟进程死亡 | URI 从 SafePreferences 恢复 | 2026.07 hotfix |
| 22.18 | **OnboardingState 单源事实** | P0 | 冷启动验证 | NavGraph 和 ViewModel 读到同一值 | 2026.07 hotfix |
| 22.19 | **FontScale 运行时变化限制** | P1 | 系统设置改超大字体 | Activity.recreate() 触发 | 2026.07 hotfix |
| 22.20 | **DeviceOptimizer 用 MemAvailable 而非 MemTotal** | P1 | 验证内存检测 | 返回可用内存而非总内存 | 2026.07 hotfix |

---

## 测试优先级定义

| 优先级 | 定义 | 典型场景 |
|--------|------|---------|
| **P0** | 核心功能，必须通过 | 启动不崩溃、崩溃捕获、引导流程、权限请求、导航、签名校验 |
| **P1** | 重要功能，常规测试 | 性能指标、OEM 兼容性、混淆压缩、通知渠道、深层链接 |
| **P2** | 边缘场景，最佳实践 | 模拟器检测、ROOT 检测、Chromebook 适配、小众 OEM |

---

## 测试环境矩阵

| 环境 | 设备 | Android 版本 | 用途 |
|------|------|-------------|------|
| 真机 | OPPO Find X9 Pro | Android 16 (API 36) | 核心优化目标，16KB 页面 |
| 真机 | Google Pixel 9 Pro | Android 15/16 (API 35/36) | 标杆设备，edge-to-edge |
| 真机 | Samsung Galaxy S25 Ultra | Android 15 (API 35) | OneUI 兼容 |
| 真机 | Xiaomi 15 Ultra | Android 15 (API 35) | HyperOS 兼容 |
| 真机 | OPPO Find X8 Pro | Android 14 (API 34) | 核心优化目标 |
| 真机 | Samsung Galaxy S24 | Android 14 (API 34) | OneUI 兼容 |
| 真机 | Google Pixel 8 | Android 14 (API 34) | 预测性返回 |
| 真机 | Xiaomi 14 | Android 14 (API 34) | MIUI 兼容 |
| 真机 | OnePlus 12 | Android 14 (API 34) | OxygenOS 兼容 |
| 模拟器 | Android Emulator x86_64 | API 26-36 | CI 自动化测试 |
| 真机 | 低端设备 (4GB RAM) | Android 10-13 | 自适应降级测试 |
| 真机 | 折叠屏 (Galaxy Z Fold) | Android 14 | 多窗口适配 |

---

## 测试执行建议

1. **P0 用例 100% 通过** 才能发布。
2. **P1 用例 95% 通过** 才能发布，未通过的必须有明确修复计划。
3. **P2 用例** 尽力覆盖，已知问题记录在 release notes。
4. **每次提交** 自动运行 CI (instrumentation test + lint + detekt)。
5. **每次 release 构建** 在真机矩阵上完整执行 P0 + P1。
6. **每个 hotfix** 在对应回归归属版本上额外验证。
7. **新功能上线** 同步更新此测试清单。
8. **崩溃率监控** 通过 CrashReporter 持续跟踪，目标 < 0.1%。

---

> **文档版本**: v1.0  
> **生成日期**: 2026-07-02  
> **适用项目**: RapidRAW-Android v1.6.8 (versionCode=10608)  
> **测试基线**: minSdk=26, targetSdk=36, compileSdk=36