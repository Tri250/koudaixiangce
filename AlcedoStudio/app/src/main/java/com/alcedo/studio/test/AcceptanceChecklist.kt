package com.alcedo.studio.test

object AcceptanceChecklist {

    val checklist = listOf(
        CheckItem(
            id = "P0-001",
            category = "项目构建",
            title = "Gradle 构建成功",
            description = "执行 ./gradlew assembleDebug 应成功构建，无编译错误",
            expected = "构建成功，APK 生成",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P0-002",
            category = "项目构建",
            title = "Hilt 依赖注入配置正确",
            description = "Hilt 注解处理器正常工作，无编译期错误",
            expected = "Application 类使用 @HiltAndroidApp 正常启动",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P0-003",
            category = "数据库",
            title = "Room 数据库初始化",
            description = "AppDatabase 单例模式正确，首次启动自动创建数据库",
            expected = "数据库文件正常创建，可执行 CRUD 操作",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P0-004",
            category = "数据库",
            title = "数据库迁移与备份",
            description = "数据库损坏时自动备份并重建",
            expected = "异常后应用仍可启动，备份文件存在",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P0-005",
            category = "导航",
            title = "Navigation Component 配置",
            description = "导航图定义完整，各屏幕路由正确",
            expected = "可在相册、编辑器、设置等屏幕间正确跳转",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P0-006",
            category = "图片处理",
            title = "RAW 文件识别",
            description = "支持主流 RAW 格式（DNG、CR2、NEF、ARW、RAF 等）",
            expected = "正确识别 RAW 文件，显示 RAW 格式标识",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P0-007",
            category = "图片处理",
            title = "RAW 解码降级策略",
            description = "原生解码器不可用时自动降级到系统解码",
            expected = "所有解码策略依次尝试，最终返回有效图像",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P0-008",
            category = "图片处理",
            title = "EXIF 信息读取",
            description = "正确读取相机型号、光圈、快门、ISO 等 EXIF 数据",
            expected = "详情页显示完整的 EXIF 信息",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P0-009",
            category = "内存管理",
            title = "Bitmap 内存池",
            description = "Bitmap 复用机制有效，防止 OOM",
            expected = "连续打开多张图片后内存稳定，无崩溃",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P0-010",
            category = "稳定性",
            title = "Crash 捕获",
            description = "CrashHandler 正确捕获未处理异常",
            expected = "崩溃后自动重启应用，不显示系统崩溃对话框",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P0-011",
            category = "稳定性",
            title = "ANR 监控",
            description = "ANRWatchdog 监控主线程阻塞",
            expected = "主线程阻塞超过阈值时记录日志并告警",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P1-001",
            category = "API 兼容性",
            title = "API Level 检查",
            description = "ApiLevel 工具类正确判断 Android 版本",
            expected = "在 Android 8.0+ 设备上功能正常",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P1-002",
            category = "API 兼容性",
            title = "Scoped Storage 适配",
            description = "Android 10+ 使用 MediaStore 和 SAF",
            expected = "可正常读取和写入外部存储",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P1-003",
            category = "日志",
            title = "统一日志管理",
            description = "L.kt 统一日志接口，支持调试开关",
            expected = "日志输出格式统一，Release 版本可关闭",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P1-004",
            category = "混淆",
            title = "ProGuard 规则",
            description = "核心类不被混淆，序列化正常",
            expected = "Release 构建成功，运行无异常",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P1-005",
            category = "包结构",
            title = "包结构统一",
            description = "所有核心类位于 com.alcedo.studio 包下",
            expected = "无跨包引用问题，代码组织清晰",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P2-001",
            category = "代码质量",
            title = "魔法数字提取",
            description = "Constants.kt 集中管理所有魔法数字",
            expected = "代码中无硬编码数字，易于维护",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P2-002",
            category = "代码质量",
            title = "资源文件组织",
            description = "strings、colors、dimens 等资源文件完整",
            expected = "无硬编码字符串和颜色值",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P2-003",
            category = "性能",
            title = "图片加载性能",
            description = "缩略图加载流畅，内存占用合理",
            expected = "列表滚动流畅，无明显卡顿",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P2-004",
            category = "性能",
            title = "解码性能",
            description = "RAW 解码时间合理，有进度反馈",
            expected = "大文件解码时显示进度条",
            status = Status.PENDING
        ),
        CheckItem(
            id = "P2-005",
            category = "UX",
            title = "错误提示",
            description = "操作失败时有清晰的错误提示",
            expected = "Toast/Snackbar 显示友好的错误信息",
            status = Status.PENDING
        )
    )

    data class CheckItem(
        val id: String,
        val category: String,
        val title: String,
        val description: String,
        val expected: String,
        var status: Status
    )

    enum class Status {
        PASS,
        FAIL,
        PENDING,
        SKIP
    }

    fun printReport(): String {
        val passCount = checklist.count { it.status == Status.PASS }
        val failCount = checklist.count { it.status == Status.FAIL }
        val pendingCount = checklist.count { it.status == Status.PENDING }
        val skipCount = checklist.count { it.status == Status.SKIP }

        val sb = StringBuilder()
        sb.append("=".repeat(80)).append("\n")
        sb.append("         AlcedoStudio 验收测试报告\n")
        sb.append("=".repeat(80)).append("\n")
        sb.append("\n")
        sb.append("测试统计:\n")
        sb.append("  通过: $passCount\n")
        sb.append("  失败: $failCount\n")
        sb.append("  待测试: $pendingCount\n")
        sb.append("  跳过: $skipCount\n")
        sb.append("\n")
        sb.append("测试详情:\n")
        sb.append("-".repeat(80)).append("\n")

        checklist.groupBy { it.category }.forEach { (category, items) ->
            sb.append("\n【$category】\n")
            items.forEach { item ->
                val statusIcon = when (item.status) {
                    Status.PASS -> "✓"
                    Status.FAIL -> "✗"
                    Status.PENDING -> "○"
                    Status.SKIP -> "-"
                }
                sb.append("  $statusIcon ${item.id}: ${item.title}\n")
                sb.append("     描述: ${item.description}\n")
                sb.append("     预期: ${item.expected}\n")
            }
        }

        sb.append("\n")
        sb.append("=".repeat(80)).append("\n")
        val overallStatus = if (failCount > 0) "未通过" else if (pendingCount > 0) "部分通过" else "通过"
        sb.append("验收结果: $overallStatus\n")
        sb.append("=".repeat(80)).append("\n")

        return sb.toString()
    }
}
