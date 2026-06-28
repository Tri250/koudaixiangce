package com.rapidraw.data.model

import java.util.UUID

/**
 * 编辑历史中的单个条目，支持树形分支结构。
 * 每个条目记录一次调整操作的快照及描述。
 */
data class EditHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val adjustments: Adjustments,
    val description: String,
    val parentId: String? = null,
    val children: MutableList<EditHistoryEntry> = mutableListOf(),
)

/**
 * 编辑历史树，支持类 Git 的分支结构。
 * [root] 为初始状态，[current] 为当前所处节点，
 * [currentBranch] 记录从根节点到当前节点的路径（entry ID 列表）。
 */
data class EditHistoryTree(
    val root: EditHistoryEntry,
    var current: EditHistoryEntry,
    var currentBranch: MutableList<String>,
) {

    /**
     * 在当前节点下追加新的编辑记录（线性前进）。
     */
    fun pushEntry(description: String, adjustments: Adjustments): EditHistoryEntry {
        val entry = EditHistoryEntry(
            adjustments = adjustments,
            description = description,
            parentId = current.id,
        )
        current.children.add(entry)
        current = entry
        currentBranch.add(entry.id)
        return entry
    }

    /**
     * 从指定节点创建分支。返回新分支的根条目。
     */
    fun branchFrom(entry: EditHistoryEntry, description: String, adjustments: Adjustments): EditHistoryEntry {
        val branched = EditHistoryEntry(
            adjustments = adjustments,
            description = description,
            parentId = entry.id,
        )
        entry.children.add(branched)
        current = branched
        // 重建分支路径：从 root 到 entry，再追加新节点
        currentBranch = buildPathTo(entry).toMutableList()
        currentBranch.add(branched.id)
        return branched
    }

    /**
     * 跳转到指定历史条目，更新 current 及 currentBranch。
     */
    fun jumpTo(entry: EditHistoryEntry) {
        current = entry
        currentBranch = buildPathTo(entry).toMutableList()
    }

    /**
     * 从根节点遍历树查找指定 ID 的条目。
     */
    fun findById(id: String): EditHistoryEntry? {
        return findByIdInSubtree(root, id)
    }

    /**
     * 获取从根节点到指定条目的路径上所有条目。
     */
    fun getEntriesOnBranch(entry: EditHistoryEntry): List<EditHistoryEntry> {
        val path = buildPathTo(entry)
        return path.mapNotNull { findById(it) }
    }

    /**
     * 获取所有叶子节点（没有子节点的条目，即各分支的末端）。
     */
    fun getAllLeaves(): List<EditHistoryEntry> {
        val leaves = mutableListOf<EditHistoryEntry>()
        collectLeaves(root, leaves)
        return leaves
    }

    private fun collectLeaves(entry: EditHistoryEntry, leaves: MutableList<EditHistoryEntry>) {
        if (entry.children.isEmpty()) {
            leaves.add(entry)
        } else {
            entry.children.forEach { collectLeaves(it, leaves) }
        }
    }

    private fun buildPathTo(target: EditHistoryEntry): List<String> {
        val path = mutableListOf<String>()
        findPath(root, target.id, path)
        return path
    }

    private fun findPath(node: EditHistoryEntry, targetId: String, path: MutableList<String>): Boolean {
        path.add(node.id)
        if (node.id == targetId) return true
        for (child in node.children) {
            if (findPath(child, targetId, path)) return true
        }
        path.removeAt(path.lastIndex)
        return false
    }

    private fun findByIdInSubtree(node: EditHistoryEntry, id: String): EditHistoryEntry? {
        if (node.id == id) return node
        for (child in node.children) {
            val found = findByIdInSubtree(child, id)
            if (found != null) return found
        }
        return null
    }
}

/**
 * 根据调整参数字段 key 和变更值生成人类可读的描述。
 */
fun describeAdjustmentChange(key: String, value: Float): String {
    return when (key) {
        "exposure" -> "曝光 ${String.format("%.1f", value)}"
        "brightness" -> "亮度 ${String.format("%.1f", value)}"
        "contrast" -> "对比度 ${String.format("%.0f", value)}"
        "highlights" -> "高光 ${String.format("%.0f", value)}"
        "shadows" -> "阴影 ${String.format("%.0f", value)}"
        "whites" -> "白色 ${String.format("%.0f", value)}"
        "blacks" -> "黑色 ${String.format("%.0f", value)}"
        "temperature" -> "色温 ${String.format("%.0f", value)}"
        "tint" -> "色调 ${String.format("%.0f", value)}"
        "saturation" -> "饱和度 ${String.format("%.0f", value)}"
        "vibrance" -> "自然饱和度 ${String.format("%.0f", value)}"
        "sharpness" -> "锐化 ${String.format("%.0f", value)}"
        "clarity" -> "清晰度 ${String.format("%.0f", value)}"
        "dehaze" -> "去雾 ${String.format("%.0f", value)}"
        "vignetteAmount" -> "暗角 ${String.format("%.0f", value)}"
        "grainAmount" -> "颗粒 ${String.format("%.0f", value)}"
        "toneLevel" -> "影调 ${String.format("%.1f", value)}"
        "filmIntensity" -> "滤镜强度 ${String.format("%.0f", (value * 100))}%"
        "softGlow" -> "柔光 ${String.format("%.0f", (value * 100))}%"
        "greenMagenta" -> "青品 ${String.format("%.1f", value)}"
        "rotation" -> "旋转 ${String.format("%.0f", value)}°"
        "orientationSteps" -> "旋转"
        "flipHorizontal" -> "水平翻转"
        "flipVertical" -> "垂直翻转"
        "cropAspectRatio" -> "裁剪 ${describeAspectRatio(value)}"
        "lutIntensity" -> "LUT 强度 ${String.format("%.0f", value)}%"
        else -> "$key ${String.format("%.1f", value)}"
    }
}

private fun describeAspectRatio(value: Float): String {
    return when {
        value == 0f -> "自由"
        value == 1f -> "1:1"
        value == 4f / 3f -> "4:3"
        value == 3f / 2f -> "3:2"
        value == 16f / 9f -> "16:9"
        value == 65f / 24f -> "65:24"
        value == 2.35f -> "2.35:1"
        value == 9f / 16f -> "9:16"
        else -> String.format("%.2f", value)
    }
}
