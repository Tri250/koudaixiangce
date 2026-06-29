package com.rapidraw.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rapidraw.R

/**
 * ColorOS 16 字体系统 — OPPO Find X9 摄影编辑器
 *
 * OPPO Find X9 / ColorOS 16 设计规范（资深产品经理级）：
 * - 第一优先：OPPO Sans 系统字体（OPPO 设备自带，需从 res/font 显式加载）
 * - 第二优先：Roboto（Google 设备默认，与 OPPO Sans 同 x-height）
 * - 第三优先：系统 SansSerif（全局回退）
 * - 中文阅读优化：行高 1.6x–1.8x（ColorOS 16 中文正文标准），字间距微收紧
 * - 正文最小 14sp（无障碍友好，Find X9 大屏阅读舒适度）
 * - 等宽数字：滑块值、EXIF、示波器刻度必须等宽，避免跳动
 *
 * 字体加载策略：
 * 1. 若 res/font/ 存在 OPPO Sans 子集，通过 Font(R.font.*) 显式加载
 * 2. 若字体文件缺失或加载失败，自动降级到系统 SansSerif
 * 3. 通过 FontFamily 权重映射，确保 TextStyle fontWeight 生效
 */
private fun loadOPPOSansFamily(): FontFamily? = try {
    FontFamily(
        Font(R.font.opposans_regular, FontWeight.W400, FontStyle.Normal),
        Font(R.font.opposans_medium, FontWeight.W500, FontStyle.Normal),
        Font(R.font.opposans_bold, FontWeight.W700, FontStyle.Normal),
    )
} catch (_: Exception) {
    null
}

/** OPPO Sans 字体家族（可能为 null，若资源缺失） */
val OPPOSansFamily: FontFamily? = loadOPPOSansFamily()

/** 系统回退字体栈：OPPO Sans -> SansSerif */
val AppFontFamily: FontFamily = OPPOSansFamily ?: FontFamily.SansSerif

/** 等宽字体栈（用于数值显示：滑块值、坐标、EXIF 等） */
val MonoFontFamily: FontFamily = FontFamily.Monospace

/**
 * ColorOS 16 字号阶梯 + 中文行距优化
 *
 * 设计原则：
 * - Display：标题大字（启动页、空状态）
 * - Headline：页面标题、弹窗标题
 * - Title：卡片标题、分组标题
 * - Body：正文、说明文字（最小 14sp，行高 1.6x–1.8x）
 * - Label：按钮、Tab、Badge（最小 11sp，仅用于辅助标签）
 *
 * 行距规范：
 * - 中文正文 ≥ 1.6x（14sp -> 24sp，16sp -> 26sp）
 * - 英文/数字标签 1.4x–1.5x
 * - 大标题 1.2x–1.3x（紧凑有力）
 * - 编辑器数值 1.0x–1.2x（紧凑对齐）
 *
 * 所有正文必须 ≥ BodyMedium(14sp)，避免 12sp 用于正文（无障碍）
 */
val RapidRawTypography = Typography(
    // ── Display：超大标题 ─────────────────────────────────────────
    displayLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W700,
        fontSize = 36.sp,
        lineHeight = 46.sp,           // 1.28x，大标题紧凑有力
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W700,
        fontSize = 28.sp,
        lineHeight = 36.sp,           // 1.29x
        letterSpacing = (-0.25).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W700,
        fontSize = 24.sp,
        lineHeight = 32.sp,           // 1.33x
        letterSpacing = 0.sp,
    ),

    // ── Headline：页面/弹窗标题 ───────────────────────────────────
    headlineLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W700,
        fontSize = 22.sp,
        lineHeight = 30.sp,           // 1.36x
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 20.sp,
        lineHeight = 28.sp,           // 1.4x
        letterSpacing = 0.15.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 18.sp,
        lineHeight = 26.sp,           // 1.44x
        letterSpacing = 0.sp,
    ),

    // ── Title：卡片/分组标题 ──────────────────────────────────────
    titleLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 18.sp,
        lineHeight = 26.sp,           // 1.44x
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 16.sp,
        lineHeight = 24.sp,           // 1.5x
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 14.sp,
        lineHeight = 22.sp,           // 1.57x
        letterSpacing = 0.1.sp,
    ),

    // ── Body：正文（最小 14sp，中文行高 1.6x–1.8x，无障碍友好）──────
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        lineHeight = 26.sp,           // 1.625x，中文舒适阅读
        letterSpacing = 0.25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 14.sp,
        lineHeight = 24.sp,           // 1.71x，中文舒适阅读（ColorOS 16 标准）
        letterSpacing = 0.15.sp,      // 中文微收紧，避免字间过散
    ),
    bodySmall = TextStyle(
        // 仅用于辅助说明、英文标签，不用于中文正文
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 12.sp,
        lineHeight = 18.sp,           // 1.5x
        letterSpacing = 0.2.sp,
    ),

    // ── Label：按钮、Tab、Badge ───────────────────────────────────
    labelLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 20.sp,           // 1.43x
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 12.sp,
        lineHeight = 18.sp,           // 1.5x
        letterSpacing = 0.25.sp,
    ),
    labelSmall = TextStyle(
        // 仅用于 Badge、状态标签等辅助元素
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 11.sp,
        lineHeight = 16.sp,           // 1.45x
        letterSpacing = 0.3.sp,
    ),
)

/**
 * 编辑器专用扩展字号系统（ColorOS 16 摄影编辑场景细分）
 *
 * 摄影编辑场景需要比 Material 3 标准更细分的字号：
 * - 滑块数值需要等宽字体（避免数值跳动时宽度变化）
 * - Tab 标签需要比标题更小但仍清晰
 * - 工具提示需要紧凑但可读
 * - EXIF 信息需要等宽对齐
 * - 面板标题使用 Medium 字重，区分层级
 *
 * 所有编辑器组件应优先使用这些语义化样式，严禁硬编码 sp。
 */
object EditorTypography {

    /** 底部 Tab 标签（AI / 滤镜 / 调节 / 构图 / 导出） */
    val tabBarLabel = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 11.sp,
        lineHeight = 14.sp,           // 1.27x，紧凑标签
        letterSpacing = 0.3.sp,
    )

    /** Tab 标签（选中态，加重字重 + 哈苏橙） */
    val tabBarLabelActive = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W700,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp,
    )

    /** 滑块数值显示（等宽，避免数值跳动，紧凑行高） */
    val sliderValue = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 13.sp,
        lineHeight = 16.sp,           // 1.23x，数值紧凑对齐
        letterSpacing = 0.sp,
    )

    /** 滑块标签（如"曝光"、"对比度"） */
    val sliderLabel = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 13.sp,
        lineHeight = 18.sp,           // 1.38x
        letterSpacing = 0.1.sp,
    )

    /** 工具栏图标下标签（紧凑） */
    val toolbarLabel = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.25.sp,
    )

    /** Badge 文字（如"已优化"、"HDR"） */
    val badge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp,
    )

    /** EXIF 信息（等宽对齐，紧凑） */
    val exifInfo = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    )

    /** 范围/示波器刻度（等宽，极紧凑） */
    val scopeScale = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.sp,
    )

    /** 直方图/示波器标题 */
    val scopeTitle = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
    )

    /** 浮动工具栏提示文字 */
    val floatingHint = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.15.sp,
    )

    /** 大号数值（如导出进度百分比） */
    val largeValue = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    )

    /** 面板分组标题（如"导出设置"、"裁剪比例"） */
    val panelTitle = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 16.sp,
        lineHeight = 24.sp,           // 1.5x
        letterSpacing = 0.1.sp,
    )

    /** 面板分组副标题/说明 */
    val panelSubtitle = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 13.sp,
        lineHeight = 20.sp,           // 1.54x
        letterSpacing = 0.15.sp,
    )

    /** 按钮文字（主要操作） */
    val buttonPrimary = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp,
    )

    /** 按钮文字（次要操作） */
    val buttonSecondary = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    )

    /** 卡片标题（胶片名称、LUT 名称） */
    val cardTitle = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    )

    /** 卡片副标题（英文名称、描述） */
    val cardSubtitle = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.15.sp,
    )

    /** 空状态/提示文字（大字号，引导用户） */
    val emptyState = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.25.sp,
    )
}
