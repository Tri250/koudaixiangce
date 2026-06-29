package com.rapidraw.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

/**
 * ColorOS 16 色彩系统 — OPPO Find X9 摄影编辑器（资深产品经理级优化）
 *
 * 设计原则：
 * 1. AMOLED 深黑基底（#060606）：Find X9 AMOLED 屏省电 + 摄影编辑纯净背景
 * 2. 分层表面系统（Surface 1/2/3/4）：通过白 alpha 叠加模拟物理层级，而非灰阶
 * 3. 哈苏橙克制使用：仅品牌时刻（导出/取景/胶片选中），交互元素用白色系
 * 4. 文字三级对比：Primary 95% / Secondary 70% / Tertiary 45%（WCAG AA+）
 * 5. 功能色高饱和：示波器/溢出警告需在深黑背景上高对比可见
 * 6. 新增：品牌动效色彩（脉冲、呼吸、辉光渐变）用于 AI 处理/导出反馈
 *
 * 兼容性：保留所有旧 token 名（EditorBackground / TextPrimary 等），
 * 内部值升级到 ColorOS 16 规范。
 */
object ColorOS16Colors {

    // ── AMOLED 深黑基底（Find X9 屏幕优化）──────────────────────────

    /** AMOLED 纯黑基底（背景层，最底） */
    val AmoledBlack: Color = Color(0xFF060606)

    /** 表面层级 1：卡片、工具栏底色（白 4% 叠加） */
    val Surface1: Color = Color(0xFF0F0F10)

    /** 表面层级 2：底部面板、弹窗底色（白 9% 叠加） */
    val Surface2: Color = Color(0xFF161618)

    /** 表面层级 3：模态弹窗、抽屉（白 14% 叠加） */
    val Surface3: Color = Color(0xFF1E1E22)

    /** 表面层级 4：高亮卡片、选中态（白 18% 叠加） */
    val Surface4: Color = Color(0xFF26262B)

    /** 描边/分割线（白 8%） */
    val Hairline: Color = Color(0x14FFFFFF)

    /** 描边/分割线（强调态，白 16%） */
    val HairlineStrong: Color = Color(0x29FFFFFF)

    // ── 文字色阶（WCAG AA+ 对比度）──────────────────────────────────

    /** 主文字：标题、数值（95% 白，对比度 12:1） */
    val TextHigh: Color = Color(0xFFF5F5F7)

    /** 次文字：标签、说明（70% 白，对比度 8:1） */
    val TextMedium: Color = Color(0xFFB4B4BA)

    /** 辅助文字：提示、占位（45% 白，对比度 4.5:1） */
    val TextLow: Color = Color(0xFF7A7A82)

    /** 禁用文字（30% 白） */
    val TextDisabled: Color = Color(0x4DFFFFFF)

    /** 辅助文字别名（兼容旧代码引用 ColorOS16Colors.TextTertiary） */
    val TextTertiary: Color = TextLow

    // ── 哈苏橙品牌色系（克制使用，精准调色）──────────────────────────

    /** 哈苏橙核心色（仅品牌时刻：导出按钮、取景器四角、胶片选中） */
    val HasselbladOrangeCore: Color = Color(0xFFE8600C)

    /** 哈苏橙亮色变体（Hover/高亮态） */
    val HasselbladOrangeBright: Color = Color(0xFFF2803F)

    /** 哈苏橙深色变体（按压态） */
    val HasselbladOrangeDeep: Color = Color(0xFFB84D0A)

    /** 哈苏橙柔光（背景叠加、徽章） */
    val HasselbladOrangeGlow: Color = Color(0x80E8600C)

    /** 哈苏橙极淡（背景 tint，12% 透明度） */
    val HasselbladOrangeTint: Color = Color(0x1FE8600C)

    // ── 品牌动效色彩（AI 处理/导出反馈）──────────────────────────────

    /** 哈苏橙脉冲（呼吸动画起始色） */
    val HasselbladPulseStart: Color = Color(0xFFFFA060)

    /** 哈苏橙脉冲（呼吸动画结束色） */
    val HasselbladPulseEnd: Color = Color(0xFFE8600C)

    /** AI 处理辉光（智能优化时的环境光晕） */
    val AiGlowAmbient: Color = Color(0x40E8600C)

    /** 导出成功绿（与哈苏橙形成品牌互补） */
    val ExportSuccess: Color = Color(0xFF30D158)

    // ── 功能色（高饱和，深黑背景可见）──────────────────────────────

    /** 溢出警告红（高光溢出） */
    val ClippingRedVivid: Color = Color(0xFFFF453A)

    /** 溢出警告蓝（阴影溢出） */
    val ClippingBlueVivid: Color = Color(0xFF0A84FF)

    /** 成功绿 */
    val SuccessGreenVivid: Color = Color(0xFF30D158)

    /** 警告黄 */
    val WarningYellowVivid: Color = Color(0xFFFFD60A)

    /** AI 处理紫（AI 功能标识） */
    val AiPurple: Color = Color(0xFFBF5AF2)

    /** 信息蓝（提示、链接） */
    val InfoBlue: Color = Color(0xFF64D2FF)

    // ── 示波器专用色（摄影专业工具）────────────────────────────────

    /** 波形/直方图 trace 绿 */
    val ScopeTraceGreen: Color = Color(0xFF30D158)

    /** 矢量图肤色指示 */
    val ScopeSkinTone: Color = Color(0xFFFF9F0A)

    /** RGB 通道色 */
    val ScopeChannelR: Color = Color(0xFFFF453A)
    val ScopeChannelG: Color = Color(0xFF30D158)
    val ScopeChannelB: Color = Color(0xFF0A84FF)

    /** 矢量图目标圈 */
    val ScopeTargetRing: Color = Color(0x55FFFFFF)

    // ── 白色系交互色（替代橙色的交互元素）──────────────────────────

    /** 滑块轨道填充（87% 白） */
    val SliderFillWhite: Color = Color(0xDEFFFFFF)

    /** 滑块轨道空（20% 白） */
    val SliderEmptyWhite: Color = Color(0x33FFFFFF)

    /** 滑块拇指（94% 白） */
    val SliderThumbWhite: Color = Color(0xF0FFFFFF)

    /** Tab 选中下划线（87% 白） */
    val TabUnderlineWhite: Color = Color(0xDEFFFFFF)

    /** Tab 选中文字（94% 白） */
    val TabTextActiveWhite: Color = Color(0xF0FFFFFF)

    /** Tab 未选中文字（60% 白） */
    val TabTextInactiveWhite: Color = Color(0x99FFFFFF)

    /** 液态玻璃基础白（12% 透明度，通用玻璃底色） */
    val LiquidGlassBase: Color = Color(0x1FFFFFFF)

    /** 液态玻璃高光白（32% 透明度，顶部折射） */
    val LiquidGlassHighlightWhite: Color = Color(0x52FFFFFF)

    /** 液态玻璃边缘白（18% 透明度，侧边光晕） */
    val LiquidGlassEdgeWhite: Color = Color(0x2EFFFFFF)
}

// ═══════════════════════════════════════════════════════════════════
// 兼容层：旧 token 名 → 新 ColorOS 16 值
// 保留所有外部引用名，仅升级内部色值。
// ═══════════════════════════════════════════════════════════════════

// ── 哈苏橙主色系（保持名称兼容）──────────────────────────────────
val HasselbladOrange = ColorOS16Colors.HasselbladOrangeCore
val HasselbladOrangeLight = ColorOS16Colors.HasselbladOrangeBright
val HasselbladOrangeBright = ColorOS16Colors.HasselbladOrangeBright
val HasselbladOrangeDark = ColorOS16Colors.HasselbladOrangeDeep
val HasselbladOrangeDeep = ColorOS16Colors.HasselbladOrangeDeep
val HasselbladOrangeMuted = ColorOS16Colors.HasselbladOrangeGlow

// ── 暗色系（升级到 AMOLED 深黑 + 分层表面）──────────────────────
val EditorBackground = ColorOS16Colors.AmoledBlack
val EditorSurface = ColorOS16Colors.Surface2
val EditorSurfaceVariant = ColorOS16Colors.Surface3
val EditorBorder = ColorOS16Colors.HairlineStrong

// ── 文字色（升级对比度）──────────────────────────────────────────
val TextPrimary = ColorOS16Colors.TextHigh
val TextSecondary = ColorOS16Colors.TextMedium
val TextTertiary = ColorOS16Colors.TextLow

// ── 功能色（升级饱和度）──────────────────────────────────────────
val ClippingRed = ColorOS16Colors.ClippingRedVivid
val ClippingBlue = ColorOS16Colors.ClippingBlueVivid
val SuccessGreen = ColorOS16Colors.SuccessGreenVivid
val WarningYellow = ColorOS16Colors.WarningYellowVivid

// ── 渐变（AMOLED 深度渐变 + 品牌动效）────────────────────────────

/** 哈苏橙水平渐变（导出按钮、品牌条） */
val HasselbladGradient = Brush.horizontalGradient(
    colors = listOf(
        ColorOS16Colors.HasselbladOrangeDeep,
        ColorOS16Colors.HasselbladOrangeCore,
        ColorOS16Colors.HasselbladOrangeBright,
    ),
)

/** 哈苏橙脉冲渐变（AI 处理呼吸动画） */
val HasselbladPulseGradient = Brush.radialGradient(
    colors = listOf(
        ColorOS16Colors.HasselbladPulseStart.copy(alpha = 0.6f),
        ColorOS16Colors.HasselbladPulseEnd.copy(alpha = 0.2f),
        Color.Transparent,
    ),
)

/** AMOLED 顶部渐变（状态栏遮罩，从黑到透明） */
val AmoledTopGradient = Brush.verticalGradient(
    colors = listOf(
        ColorOS16Colors.AmoledBlack,
        ColorOS16Colors.AmoledBlack.copy(alpha = 0.85f),
        Color.Transparent,
    ),
    startY = 0f,
    endY = 120f,
)

/** AMOLED 底部渐变（底部 Tab 栏遮罩，从透明到黑） */
val AmoledBottomGradient = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        ColorOS16Colors.AmoledBlack.copy(alpha = 0.85f),
        ColorOS16Colors.AmoledBlack,
    ),
    startY = 0f,
    endY = 120f,
)

/** 哈苏橙径向辉光（AI 处理背景） */
val HasselbladRadialGlow = Brush.radialGradient(
    colors = listOf(
        ColorOS16Colors.HasselbladOrangeCore.copy(alpha = 0.25f),
        ColorOS16Colors.HasselbladOrangeCore.copy(alpha = 0.08f),
        Color.Transparent,
    ),
)

/** 液态玻璃高光渐变（顶部折射） */
val LiquidGlassHighlight = Brush.verticalGradient(
    colors = listOf(
        ColorOS16Colors.LiquidGlassHighlightWhite,
        Color.White.copy(alpha = 0.08f),
        Color.Transparent,
    ),
    startY = 0f,
    endY = 200f,
)

/** 液态玻璃边缘渐变（侧边光晕） */
val LiquidGlassEdgeGlow = Brush.horizontalGradient(
    colors = listOf(
        ColorOS16Colors.LiquidGlassEdgeWhite,
        Color.Transparent,
        Color.White.copy(alpha = 0.12f),
    ),
)

/** AI 处理环境辉光（大面积柔和光晕） */
val AiAmbientGlow = Brush.radialGradient(
    colors = listOf(
        ColorOS16Colors.AiGlowAmbient,
        ColorOS16Colors.HasselbladOrangeCore.copy(alpha = 0.05f),
        Color.Transparent,
    ),
)

/** 导出成功渐变（品牌互补色） */
val ExportSuccessGradient = Brush.horizontalGradient(
    colors = listOf(
        ColorOS16Colors.HasselbladOrangeDeep,
        ColorOS16Colors.ExportSuccess,
    ),
)

// ── 透明度变体（供 Theme ColorScheme 使用）──────────────────────
val HasselbladOrange20Percent = HasselbladOrange.copy(alpha = 0.20f)
val HasselbladOrange10Percent = HasselbladOrange.copy(alpha = 0.10f)
val HasselbladOrangeTint = ColorOS16Colors.HasselbladOrangeTint

// ── UI 交互色（白色系，替代橙色用于交互元素）──────────────────────
val SliderTrackFill = ColorOS16Colors.SliderFillWhite
val SliderTrackEmpty = ColorOS16Colors.SliderEmptyWhite
val SliderThumb = ColorOS16Colors.SliderThumbWhite
val TabActiveUnderline = ColorOS16Colors.TabUnderlineWhite
val TabActiveText = ColorOS16Colors.TabTextActiveWhite
val TabInactiveText = ColorOS16Colors.TabTextInactiveWhite
val FilmCardBorder = HasselbladOrange           // 仅胶片卡片选中
val ExportButtonBg = HasselbladOrange           // 仅导出/品牌按钮
val ViewfinderCorner = HasselbladOrange         // 仅取景器四角
val BadgeBg = ColorOS16Colors.HasselbladOrangeGlow  // "已优化" 徽章
