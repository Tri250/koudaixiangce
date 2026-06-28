package com.rapidraw.ui.theme

import androidx.compose.ui.unit.dp

/**
 * ColorOS 16 间距 Token 系统
 *
 * OPPO Find X9 / ColorOS 16 设计规范：
 * - 基于 4dp 基准网格（4dp grid），所有间距为 4 的倍数
 * - 语义化命名：组件内紧凑 / 组件间标准 / 区块间宽松 / 页面边距
 * - 禁止硬编码 dp，统一通过此 Token 引用
 *
 * 间距阶梯：
 * - xxxs (2dp)  : 图标与文字内紧贴
 * - xxs  (4dp)  : 组件内元素最小间距
 * - xs   (8dp)  : 组件内元素标准间距
 * - sm   (12dp) : 组件内元素宽松间距
 * - md   (16dp) : 组件间标准间距（默认）
 * - lg   (20dp) : 组件间宽松间距
 * - xl   (24dp) : 区块间距
 * - xxl  (32dp) : 区块间大间距
 * - xxxl (40dp) : 页面级区块间距
 * - 56dp         : 底部 Tab 栏高度
 * - 64dp         : 顶部应用栏高度
 */
object Spacing {

    /** 2dp：图标与文字内紧贴 */
    val Xxxs = 2.dp

    /** 4dp：组件内元素最小间距 */
    val Xxs = 4.dp

    /** 8dp：组件内元素标准间距 */
    val Xs = 8.dp

    /** 12dp：组件内元素宽松间距 */
    val Sm = 12.dp

    /** 16dp：组件间标准间距（默认） */
    val Md = 16.dp

    /** 20dp：组件间宽松间距 */
    val Lg = 20.dp

    /** 24dp：区块间距 */
    val Xl = 24.dp

    /** 32dp：区块间大间距 */
    val Xxl = 32.dp

    /** 40dp：页面级区块间距 */
    val Xxxl = 40.dp
}

/**
 * 编辑器专用尺寸 Token
 *
 * 摄影编辑器有特殊的尺寸需求（避开系统手势区、底部 Tab 高度等），
 * 与通用 Spacing 分离，语义更清晰。
 */
object EditorDimens {

    /** 底部 5 Tab 栏高度（含安全区） */
    val TabBarHeight = 56.dp

    /** 顶部应用栏高度 */
    val TopBarHeight = 48.dp

    /** 底部面板默认高度（半屏） */
    val BottomPanelHeight = 260.dp

    /** 浮动工具栏高度 */
    val FloatingToolbarHeight = 44.dp

    /** 滑块行高度 */
    val SliderRowHeight = 48.dp

    /** 滑块拇指直径 */
    val SliderThumbSize = 22.dp

    /** 胶片卡片宽度 */
    val FilmCardWidth = 96.dp

    /** 胶片卡片高度 */
    val FilmCardHeight = 128.dp

    /** 示波器默认尺寸 */
    val ScopeSize = 140.dp

    /** 底部手势区安全高度（导航栏） */
    val NavigationBarSafeArea = 48.dp

    /** 顶部状态栏安全高度 */
    val StatusBarSafeArea = 24.dp

    /** 取景器四角线长度 */
    val ViewfinderCornerLength = 24.dp

    /** 取景器四角线粗细 */
    val ViewfinderCornerStroke = 2.dp
}
