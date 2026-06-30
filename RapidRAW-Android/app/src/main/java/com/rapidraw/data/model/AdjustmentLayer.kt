package com.rapidraw.data.model

import kotlinx.serialization.Serializable

/**
 * 调整图层 - 支持非破坏性编辑的图层叠加系统
 * 每个图层独立包含一组调整参数和蒙版，按顺序叠加
 */
@Serializable
data class AdjustmentLayer(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val opacity: Float = 1f,           // 0.0 ~ 1.0 图层不透明度
    val blendMode: BlendMode = BlendMode.NORMAL,
    val adjustments: Adjustments = Adjustments(),
    val maskType: LayerMaskType = LayerMaskType.NONE,
    val maskBitmap: ByteArray? = null,  // 蒙版位图数据（序列化时为压缩的PNG bytes）
    val filmId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class BlendMode {
    NORMAL,           // 正常
    MULTIPLY,         // 正片叠底
    SCREEN,           // 滤色
    OVERLAY,          // 叠加
    SOFT_LIGHT,       // 柔光
    HARD_LIGHT,       // 强光
    COLOR,            // 颜色
    LUMINOSITY,       // 明度
}

enum class LayerMaskType {
    NONE,             // 无蒙版
    BRUSH,            // 画笔蒙版
    RADIAL,           // 径向蒙版
    GRADIENT,         // 渐变蒙版
    AI_SEMANTIC,      // AI语义蒙版
}

/**
 * 图层堆栈 - 管理所有调整图层的有序列表
 */
@Serializable
data class LayerStack(
    val layers: List<AdjustmentLayer> = listOf(
        AdjustmentLayer(name = "背景", enabled = true)
    ),
    val activeLayerId: String = layers.firstOrNull()?.id ?: "",
) {
    fun addLayer(layer: AdjustmentLayer): LayerStack =
        copy(layers = layers + layer, activeLayerId = layer.id)

    fun removeLayer(layerId: String): LayerStack =
        copy(layers = layers.filter { it.id != layerId })

    fun updateLayer(layerId: String, transform: (AdjustmentLayer) -> AdjustmentLayer): LayerStack =
        copy(layers = layers.map { if (it.id == layerId) transform(it) else it })

    fun setActiveLayer(layerId: String): LayerStack =
        copy(activeLayerId = layerId)

    fun moveLayer(fromIndex: Int, toIndex: Int): LayerStack {
        val mutable = layers.toMutableList()
        val layer = mutable.removeAt(fromIndex)
        mutable.add(toIndex.coerceIn(0, mutable.size), layer)
        return copy(layers = mutable)
    }
}
