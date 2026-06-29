package com.rapidraw.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * ColorOS 16 字体系统
 *
 * OPPO Find X9 / ColorOS 16 设计规范：
 * - 优先使用 OPPO Sans（ColorOS 系统字体，OPPO 设备自带）
 * - 回退链：OPPO Sans → PingFang SC / Noto Sans CJK → Roboto → system sans
 * - 中文阅读优化：行高 1.5x-1.55x，字间距收紧，正文最小 14sp
 * - 摄影编辑工具类文字：使用 13sp + 等宽数字，保证数值不跳动
 */
internal val OPPOSansFamily: FontFamily = FontFamily(
    // ColorOS 16 首选系统字体（OPPO Sans / OPPO Sans 2.0）
    Font("OPPO Sans", FontWeight.Normal),
    Font("OPPO Sans", FontWeight.Bold),
    Font("OPPO Sans 2.0", FontWeight.Normal),
    Font("OPPO Sans 2.0", FontWeight.Bold),
    Font("OPPOSans", FontWeight.Normal),
    Font("OPPOSans", FontWeight.Bold),
    // 中文回退
    Font("PingFang SC", FontWeight.Normal),
    Font("PingFang SC", FontWeight.Bold),
    Font("Noto Sans CJK SC", FontWeight.Normal),
    Font("Noto Sans CJK SC", FontWeight.Bold),
    // 英文/数字回退
    Font("Roboto", FontWeight.Normal),
    Font("Roboto", FontWeight.Bold),
    Font("sans-serif", FontWeight.Normal),
    Font("sans-serif", FontWeight.Bold),
)

/** 应用主字体栈（OPPO Sans 优先 + 中文/英文系统字体回退） */
internal val AppFontFamily: FontFamily = OPPOSansFamily

/** 等宽字体栈（用于数值显示：滑块值、坐标、EXIF 等，避免宽度跳动） */
internal val MonoFontFamily: FontFamily = FontFamily(
    Font("Roboto Mono", FontWeight.Normal),
    Font("Roboto Mono", FontWeight.Bold),
    Font("SF Mono", FontWeight.Normal),
    Font("Droid Sans Mono", FontWeight.Normal),
    Font("monospace", FontWeight.Normal),
)

/**
 * ColorOS 16 字号阶梯
 *
 * 设计原则：
 * - Display：标题大字（启动页、空状态）
 * - Headline：页面标题、弹窗标题
 * - Title：卡片标题、分组标题
 * - Body：正文、说明文字（最小 14sp）
 * - Label：按钮、Tab、Badge（最小 11sp，仅用于辅助标签）
 *
 * 所有正文必须 ≥ BodyMedium(14sp)，避免 12sp 用于正文（无障碍）
 */
val RapidRawTypography = Typography(
    // ── Display：超大标题 ─────────────────────────────────────────
    displayLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W700,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W700,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.25).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W700,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),

    // ── Headline：页面/弹窗标题 ───────────────────────────────────
    headlineLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W700,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.15.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),

    // ── Title：卡片/分组标题 ──────────────────────────────────────
    titleLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),

    // ── Body：正文（最小 14sp，无障碍友好，中文 1.55x 行高）──────
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        // 仅用于辅助说明、时间戳、元数据，不用于正文
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
    ),

    // ── Label：按钮、Tab、Badge ───────────────────────────────────
    labelLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        // 仅用于 Badge、状态标签等辅助元素
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

/**
 * 编辑器专用扩展字号系统
 *
 * 摄影编辑场景需要比 Material 3 标准更细分的字号：
 * - 滑块数值需要等宽字体（避免数值跳动时宽度变化）
 * - Tab 标签需要比标题更小但仍清晰
 * - 工具提示需要紧凑但可读
 * - EXIF 信息需要等宽对齐
 *
 * 所有编辑器组件应优先使用这些语义化样式，而非硬编码 sp。
 */
object EditorTypography {

    /** Tab 标签（底部 5 Tab：AI / 滤镜 / 调整 / 构图 / 导出） */
    val tabBarLabel = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    )

    /** Tab 标签（选中态，加重字重） */
    val tabBarLabelActive = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W700,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    )

    /** 滑块数值显示（等宽 Tabular，避免数值跳动） */
    val sliderValue = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    )

    /** 滑块标签（如"曝光"、"对比度"） */
    val sliderLabel = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.15.sp,
    )

    /** 滑块标签（激活态：已调整/拖拽） */
    val sliderLabelActive = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W700,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.15.sp,
    )

    /** 工具栏图标下标签（紧凑） */
    val toolbarLabel = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
    )

    /** Badge 文字（如"已优化"、"HDR"） */
    val badge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.25.sp,
    )

    /** 按钮文字（大按钮、导出/确认） */
    val button = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    )

    /** EXIF 信息（等宽对齐） */
    val exifInfo = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    )

    /** 范围/示波器刻度（等宽） */
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
        letterSpacing = 0.4.sp,
    )

    /** 浮动工具栏提示文字 */
    val floatingHint = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.W500,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp,
    )

    /** 大号数值（如导出进度百分比） */
    val largeValue = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.W600,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    )
}
