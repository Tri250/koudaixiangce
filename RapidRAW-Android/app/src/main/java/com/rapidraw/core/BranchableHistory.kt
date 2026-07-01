package com.rapidraw.core

import android.util.Log
import com.rapidraw.data.model.Adjustments
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * 历史树中的单个节点，代表一次编辑状态快照。
 * 每个节点存储 Adjustments 快照、描述、时间戳和稳定 UUID，
 * 通过 parentId / childrenIds 构成树形结构。
 *
 * Merkle-tree 风格验证：每个节点包含 contentHash，由其 Adjustments 数据
 * 和所有子节点 contentHash 联合计算得出，确保历史完整性。
 */
@Serializable
data class HistoryNode(
    val id: String = UUID.randomUUID().toString(),
    val parentId: String? = null,
    val childrenIds: MutableList<String> = mutableListOf(),
    val adjustments: Adjustments,
    val label: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val branchName: String = "main",
    val contentHash: String = "",
) {
    /**
     * 计算节点的 Merkle 哈希值。
     * 由 Adjustments 的序列化数据 + 子节点哈希联合计算。
     * 若任一子节点被篡改，父节点哈希将不匹配。
     */
    fun computeHash(childHashes: List<String> = emptyList()): String {
        val md = MessageDigest.getInstance("SHA-256")
        // 将 Adjustments 关键字段参与哈希
        val data = buildString {
            append(id)
            append(parentId ?: "")
            append(label)
            append(timestamp)
            append(branchName)
            // 序列化 Adjustments 的核心字段
            append(adjustments.exposure)
            append(adjustments.toneLevel)
            append(adjustments.filmIntensity)
            append(adjustments.contrast)
            append(adjustments.highlights)
            append(adjustments.shadows)
            append(adjustments.whites)
            append(adjustments.blacks)
            append(adjustments.vibrance)
            append(adjustments.saturation)
            append(adjustments.temperature)
            append(adjustments.tint)
            append(adjustments.sharpness)
            append(adjustments.hslReds.hue)
            append(adjustments.hslReds.saturation)
            append(adjustments.hslReds.luminance)
            append(adjustments.hslOranges.hue)
            append(adjustments.hslOranges.saturation)
            append(adjustments.hslOranges.luminance)
            append(adjustments.hslYellows.hue)
            append(adjustments.hslYellows.saturation)
            append(adjustments.hslYellows.luminance)
            append(adjustments.hslGreens.hue)
            append(adjustments.hslGreens.saturation)
            append(adjustments.hslGreens.luminance)
            append(adjustments.hslAquas.hue)
            append(adjustments.hslAquas.saturation)
            append(adjustments.hslAquas.luminance)
            append(adjustments.hslBlues.hue)
            append(adjustments.hslBlues.saturation)
            append(adjustments.hslBlues.luminance)
            append(adjustments.hslPurples.hue)
            append(adjustments.hslPurples.saturation)
            append(adjustments.hslPurples.luminance)
            append(adjustments.hslMagentas.hue)
            append(adjustments.hslMagentas.saturation)
            append(adjustments.hslMagentas.luminance)
            // Oklab perceptual adjustments (v1.6.0 ZenFilters pipeline)
            append(adjustments.oklabHueShift)
            append(adjustments.oklabSaturation)
            append(adjustments.oklabChroma)
            append(adjustments.oklabLightness)
            append(adjustments.oklabContrast)
            append(adjustments.oklabTextureAmount)
            // 子节点哈希（Merkle-tree）
            for (childHash in childHashes.sorted()) {
                append(childHash)
            }
        }
        val digest = md.digest(data.toByteArray())
        return digest.fold("") { str, byte -> str + "%02x".format(byte) }
    }
}

/**
 * Git 风格的可分支编辑历史系统。
 * 支持在树形结构上任意历史节点创建分支、撤销/重做、合并分支等操作。
 * 线程安全（所有公开方法通过 synchronized 保护）。
 * 通过 kotlinx.serialization 支持序列化/持久化。
 * 仅存储 Adjustments 快照（差异），不存储位图，保证高效。
 *
 * Merkle-tree 验证：
 * 每个节点包含 contentHash，由 Adjustments 数据 + 子节点哈希联合计算。
 * 可通过 validateIntegrity() 方法检查整棵树的完整性。
 */
@Serializable
data class BranchableHistory(
    /** 根节点，代表原始/未编辑状态 */
    val root: HistoryNode,
    /** 所有节点按 ID 索引，便于快速查找 */
    val nodeMap: MutableMap<String, HistoryNode> = mutableMapOf(),
    /** 分支名 → 该分支上最新节点 ID 的映射 */
    val branchTips: MutableMap<String, String> = mutableMapOf(),
    /** 分支名 → 该分支的根节点 ID（分支起始点） */
    val branchRoots: MutableMap<String, String> = mutableMapOf(),
    /** 当前节点 ID */
    var currentNodeId: String = root.id,
    /** 当前分支名 */
    var currentBranchName: String = "main",
) {
    init {
        nodeMap[root.id] = root
        branchTips["main"] = root.id
        branchRoots["main"] = root.id
    }

    /**
     * 用于锁的对象。标记 @Transient 以避免序列化。
     */
    @Transient
    @Volatile
    private var _lock: Any? = null

    private val lock: Any
        get() = _lock ?: synchronized(this) { _lock ?: Any().also { _lock = it } }

    // ── 公开 API ──────────────────────────────────────────────

    /**
     * 推入新的编辑状态，追加到当前分支的当前节点之后。
     * @param adjustments 本次编辑的 Adjustments 快照
     * @param label 操作描述
     * @return 新创建的 HistoryNode
     */
    fun pushState(adjustments: Adjustments, label: String = ""): HistoryNode {
        synchronized(lock) {
            val parent = nodeMap[currentNodeId]
                ?: throw IllegalStateException("BranchableHistory current node missing: $currentNodeId")
            val node = HistoryNode(
                parentId = parent.id,
                adjustments = adjustments,
                label = label,
                branchName = currentBranchName,
            )
            // 计算 contentHash（新节点无子节点，仅基于自身数据）
            val nodeWithHash = node.copy(contentHash = node.computeHash())
            parent.childrenIds.add(nodeWithHash.id)
            nodeMap[nodeWithHash.id] = nodeWithHash
            currentNodeId = nodeWithHash.id
            branchTips[currentBranchName] = nodeWithHash.id

            // 更新父节点的 Merkle 哈希（沿路径向上传播）
            rehashPathToRoot(parent.id)

            return nodeWithHash
        }
    }

    /**
     * 撤销：回退到当前分支上的前一个节点。
     * @return 撤销后的当前节点；若无法撤销则返回 null
     */
    fun undo(): HistoryNode? {
        synchronized(lock) {
            val current = nodeMap[currentNodeId] ?: return null
            val parentId = current.parentId ?: return null
            val parent = nodeMap[parentId] ?: return null
            currentNodeId = parent.id
            return parent
        }
    }

    /**
     * 重做：前进到当前分支上的下一个节点。
     * 如果当前节点有多个子节点，选择属于当前分支的那个。
     * @return 重做后的当前节点；若无法重做则返回 null
     */
    fun redo(): HistoryNode? {
        synchronized(lock) {
            val current = nodeMap[currentNodeId] ?: return null
            // 优先选择属于当前分支的子节点
            val branchChild = current.childrenIds.firstNotNullOfOrNull { cid ->
                val child = nodeMap[cid]
                if (child != null && child.branchName == currentBranchName) child else null
            }
            if (branchChild != null) {
                currentNodeId = branchChild.id
                return branchChild
            }
            // 没有当前分支的子节点，但若 tip 在更深处，则走第一个子节点
            val firstChildId = current.childrenIds.firstOrNull() ?: return null
            val firstChild = nodeMap[firstChildId] ?: return null
            currentNodeId = firstChild.id
            return firstChild
        }
    }

    /**
     * 从指定节点创建新分支，新分支的第一个节点采用给定的 adjustments。
     * 类似 git checkout -b。
     * @param nodeId 分支起点的节点 ID
     * @param branchName 新分支名称
     * @return 新分支的第一个节点
     */
    fun branchFrom(nodeId: String, branchName: String): HistoryNode {
        synchronized(lock) {
            require(branchName !in branchTips) { "分支 '$branchName' 已存在" }
            val sourceNode = nodeMap[nodeId] ?: throw IllegalArgumentException("节点 $nodeId 不存在")
            // 创建分支根节点，继承源节点的 adjustments 作为起点
            val branchRoot = HistoryNode(
                parentId = sourceNode.id,
                adjustments = sourceNode.adjustments,
                label = "分支: $branchName",
                branchName = branchName,
            )
            val branchRootWithHash = branchRoot.copy(contentHash = branchRoot.computeHash())
            sourceNode.childrenIds.add(branchRootWithHash.id)
            nodeMap[branchRootWithHash.id] = branchRootWithHash
            branchTips[branchName] = branchRootWithHash.id
            branchRoots[branchName] = branchRootWithHash.id
            currentNodeId = branchRootWithHash.id
            currentBranchName = branchName

            // 更新 Merkle 哈希
            rehashPathToRoot(sourceNode.id)

            return branchRootWithHash
        }
    }

    /**
     * 获取当前分支从分支根到 tip 的所有节点列表。
     */
    fun getCurrentBranch(): List<HistoryNode> {
        synchronized(lock) {
            return getBranchPath(currentBranchName)
        }
    }

    /**
     * 获取所有分支名称。
     */
    fun getBranches(): List<String> {
        synchronized(lock) {
            return branchTips.keys.toList()
        }
    }

    /**
     * 切换到指定分支，将当前节点设为该分支的 tip。
     * @param branchName 目标分支名称
     * @return 切换后的当前节点；若分支不存在返回 null
     */
    fun switchBranch(branchName: String): HistoryNode? {
        synchronized(lock) {
            val tipId = branchTips[branchName] ?: return null
            currentBranchName = branchName
            currentNodeId = tipId
            return nodeMap[tipId]
        }
    }

    /**
     * 折叠/合并指定分支到其父分支。
     * 将目标分支 tip 的 Adjustments 作为新节点追加到父分支的分支根父节点上，
     * 然后删除分支结构。
     *
     * @param branchName 要折叠的分支名称
     * @return 合并后创建的新节点；若失败返回 null
     */
    fun collapseBranch(branchName: String): HistoryNode? {
        synchronized(lock) {
            if (branchName == "main") return null // 不允许折叠主分支
            val branchRootId = branchRoots[branchName] ?: return null
            val branchRoot = nodeMap[branchRootId] ?: return null
            val branchTipId = branchTips[branchName] ?: return null
            val branchTip = nodeMap[branchTipId] ?: return null

            val parentNodeId = branchRoot.parentId ?: return null
            val parentNode = nodeMap[parentNodeId] ?: return null

            // 将分支 tip 的 Adjustments 作为新节点追加到父分支
            val mergedNode = HistoryNode(
                parentId = parentNode.id,
                adjustments = branchTip.adjustments,
                label = "合并分支: $branchName",
                branchName = parentNode.branchName,
            )
            val mergedWithHash = mergedNode.copy(contentHash = mergedNode.computeHash())
            parentNode.childrenIds.add(mergedWithHash.id)
            nodeMap[mergedWithHash.id] = mergedWithHash

            // 从父节点的子列表中移除分支根
            parentNode.childrenIds.remove(branchRootId)

            // 将分支 tip 的子节点重新挂到合并节点下
            for (childId in branchTip.childrenIds) {
                val child = nodeMap[childId] ?: continue
                val updatedChild = child.copy(
                    parentId = mergedWithHash.id,
                    branchName = parentNode.branchName
                )
                nodeMap[childId] = updatedChild
                mergedWithHash.childrenIds.add(childId)
            }

            // 将分支中从 root 到 tip 的所有节点标记为已合并
            val branchNodes = collectBranchNodes(branchRootId)
            for (node in branchNodes) {
                val updated = node.copy(branchName = parentNode.branchName + "_merged")
                nodeMap[node.id] = updated
            }

            // 删除分支映射
            branchTips.remove(branchName)
            branchRoots.remove(branchName)

            // 更新父分支 tip
            branchTips[parentNode.branchName] = mergedWithHash.id

            // 更新 Merkle 哈希
            rehashPathToRoot(parentNode.id)

            // 如果当前就在被折叠的分支上，切回父分支
            if (currentBranchName == branchName) {
                currentBranchName = parentNode.branchName
                currentNodeId = mergedWithHash.id
            }

            return mergedWithHash
        }
    }

    /**
     * 是否可以撤销（当前节点不是根节点）。
     */
    fun canUndo(): Boolean {
        synchronized(lock) {
            val current = nodeMap[currentNodeId] ?: return false
            return current.parentId != null
        }
    }

    /**
     * 是否可以重做（当前节点有子节点）。
     */
    fun canRedo(): Boolean {
        synchronized(lock) {
            val current = nodeMap[currentNodeId] ?: return false
            return current.childrenIds.isNotEmpty()
        }
    }

    /**
     * 根据 ID 获取节点。
     */
    fun getNodeById(id: String): HistoryNode? {
        synchronized(lock) {
            return nodeMap[id]
        }
    }

    /**
     * 获取从指定节点到根节点的路径（含两端）。
     * 路径从指定节点开始，到根节点结束。
     */
    fun getPathToRoot(nodeId: String): List<HistoryNode> {
        synchronized(lock) {
            val path = mutableListOf<HistoryNode>()
            var currentId: String? = nodeId
            while (currentId != null) {
                val node = nodeMap[currentId] ?: break
                path.add(node)
                currentId = node.parentId
            }
            return path
        }
    }

    /**
     * 获取所有节点。
     */
    fun getAllNodes(): List<HistoryNode> {
        synchronized(lock) {
            return nodeMap.values.toList()
        }
    }

    // ── Merkle-tree 验证 ──────────────────────────────────────

    /**
     * 验证整棵历史树的完整性。
     * 从叶子节点向上递归计算 Merkle 哈希，与存储的 contentHash 比较。
     *
     * @return Pair(isValid, mismatchedNodeIds) - 整体是否有效 + 不匹配的节点 ID 列表
     */
    fun validateIntegrity(): Pair<Boolean, List<String>> {
        synchronized(lock) {
            val mismatches = mutableListOf<String>()
            validateSubtree(root.id, mismatches)
            return Pair(mismatches.isEmpty(), mismatches)
        }
    }

    /**
     * 递归验证子树的 Merkle 哈希完整性。
     */
    private fun validateSubtree(nodeId: String, mismatches: MutableList<String>) {
        val node = nodeMap[nodeId] ?: return

        // 先验证所有子节点
        val childHashes = mutableListOf<String>()
        for (childId in node.childrenIds) {
            val child = nodeMap[childId]
            if (child != null) {
                validateSubtree(childId, mismatches)
                childHashes.add(child.contentHash)
            }
        }

        // 计算当前节点的期望哈希
        val expectedHash = node.computeHash(childHashes)
        if (node.contentHash != expectedHash) {
            mismatches.add(nodeId)
        }
    }

    /**
     * 重新计算从指定节点到根节点路径上所有节点的 Merkle 哈希。
     * 在节点结构变更后调用，确保哈希链完整性。
     */
    private fun rehashPathToRoot(startNodeId: String) {
        var currentId: String? = startNodeId
        while (currentId != null) {
            val node = nodeMap[currentId] ?: break
            val childHashes = node.childrenIds.mapNotNull { cid ->
                nodeMap[cid]?.contentHash
            }
            val newHash = node.computeHash(childHashes)
            if (node.contentHash != newHash) {
                val updated = node.copy(contentHash = newHash)
                nodeMap[currentId] = updated
            }
            currentId = node.parentId
        }
    }

    // ── 内部辅助方法 ──────────────────────────────────────────

    /**
     * 获取指定分支从分支根到 tip 的节点路径。
     */
    private fun getBranchPath(branchName: String): List<HistoryNode> {
        val tipId = branchTips[branchName] ?: return emptyList()
        val branchRootId = branchRoots[branchName] ?: return emptyList()
        val path = mutableListOf<HistoryNode>()
        var currentId: String? = tipId
        val branchRoot = nodeMap[branchRootId] ?: return emptyList()
        while (currentId != null) {
            val node = nodeMap[currentId] ?: break
            path.add(0, node)
            if (currentId == branchRootId) break
            currentId = node.parentId
        }
        return path
    }

    /**
     * 收集以指定节点为根的子树中所有节点（含自身）。
     */
    private fun collectBranchNodes(rootId: String): List<HistoryNode> {
        val result = mutableListOf<HistoryNode>()
        val queue = ArrayDeque<String>()
        queue.add(rootId)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            val node = nodeMap[id] ?: continue
            result.add(node)
            queue.addAll(node.childrenIds)
        }
        return result
    }

    /**
     * 获取当前分支名。
     */
    val currentBranch: String
        get() = synchronized(lock) { currentBranchName }

    /**
     * 获取当前节点。
     */
    val currentNode: HistoryNode
        get() = synchronized(lock) {
            nodeMap[currentNodeId]
                ?: throw IllegalStateException("BranchableHistory current node missing: $currentNodeId")
        }

    /**
     * 获取所有分支名称列表。
     */
    val branchNames: List<String>
        get() = synchronized(lock) { branchTips.keys.toList() }

    /**
     * 获取历史树的统计信息。
     */
    val stats: HistoryStats
        get() = synchronized(lock) {
            HistoryStats(
                totalNodes = nodeMap.size,
                totalBranches = branchTips.size,
                currentBranch = currentBranchName,
                currentDepth = getPathToRoot(currentNodeId).size - 1,
            )
        }
}

/**
 * 历史树统计信息。
 */
data class HistoryStats(
    val totalNodes: Int,
    val totalBranches: Int,
    val currentBranch: String,
    val currentDepth: Int,
)

/**
 * 伴生对象工厂方法，便于创建 BranchableHistory 实例。
 */
object BranchableHistoryFactory {
    /**
     * 使用初始 Adjustments 创建一个新的编辑历史。
     * @param initialAdjustments 初始（未编辑）状态的 Adjustments
     * @return 新的 BranchableHistory，包含一个根节点
     */
    fun create(initialAdjustments: Adjustments = Adjustments()): BranchableHistory {
        val root = HistoryNode(
            adjustments = initialAdjustments,
            label = "初始状态",
            branchName = "main",
        )
        val rootWithHash = root.copy(contentHash = root.computeHash())
        return BranchableHistory(root = rootWithHash)
    }
}

// ═══════════════════════════════════════════════════════════════════
// 虚拟副本管理
// ═══════════════════════════════════════════════════════════════════

/**
 * 虚拟副本信息数据类。
 * 描述一个虚拟副本的元数据，不包含实际的 Adjustments 数据。
 *
 * @param id 虚拟副本唯一标识
 * @param name 副本名称
 * @param originalId 原始图像 ID
 * @param createdAt 创建时间戳（毫秒）
 * @param adjustmentCount 调整次数
 * @param thumbnailPath 缩略图路径（可为空白）
 */
data class VirtualCopyInfo(
    val id: String,
    val name: String,
    val originalId: String,
    val createdAt: Long,
    val adjustmentCount: Int,
    val thumbnailPath: String,
)

/**
 * 虚拟副本管理器。
 * 负责创建、列出、删除、提升和比较虚拟副本。
 * 虚拟副本本质上是 BranchableHistory 的分支，允许在同一原始图像上
 * 尝试不同的编辑方向而不影响原始编辑历史。
 *
 * 持久化：虚拟副本元数据保存为 JSON 文件，存储在 "virtual_copies" 目录下。
 */
class VirtualCopyManager(
    private val storageDir: File,
) {
    companion object {
        private const val TAG = "VirtualCopyManager"
        private const val VIRTUAL_COPIES_DIR = "virtual_copies"
        private const val METADATA_FILE = "metadata.json"
    }

    /** 虚拟副本存储目录 */
    private val copiesDir: File = File(storageDir, VIRTUAL_COPIES_DIR).also {
        if (!it.exists()) it.mkdirs()
    }

    /** 内存缓存：originalId → 该原始图像的所有虚拟副本信息 */
    private val cache = mutableMapOf<String, MutableList<VirtualCopyInfo>>()

    /** 内存缓存：copyId → 对应的 BranchableHistory */
    private val historyCache = mutableMapOf<String, BranchableHistory>()

    // ── 公开 API ──────────────────────────────────────────────

    /**
     * 创建虚拟副本（新分支）。
     * 将当前历史状态保存为新分支，并返回副本 ID。
     *
     * @param originalId 原始图像 ID
     * @param name 副本名称
     * @param sourceHistory 源编辑历史（将被复制为新分支）
     * @return 新创建的虚拟副本 ID
     */
    fun createVirtualCopy(
        originalId: String,
        name: String,
        sourceHistory: BranchableHistory,
    ): String {
        val copyId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // 创建虚拟副本的分支
        val branchName = "vc_$copyId"
        sourceHistory.branchFrom(sourceHistory.currentNodeId, branchName)

        // 深拷贝历史到新分支
        val copyHistory = sourceHistory.copy(
            currentNodeId = sourceHistory.currentNodeId,
            currentBranchName = branchName,
        )

        val info = VirtualCopyInfo(
            id = copyId,
            name = name,
            originalId = originalId,
            createdAt = now,
            adjustmentCount = copyHistory.getCurrentBranch().size,
            thumbnailPath = "",
        )

        // 持久化
        saveVirtualCopy(copyId, info, copyHistory)

        // 更新缓存
        cache.getOrPut(originalId) { mutableListOf() }.add(info)
        historyCache[copyId] = copyHistory

        Log.d(TAG, "Created virtual copy '$name' (id=$copyId) for original $originalId")
        return copyId
    }

    /**
     * 列出指定原始图像的所有虚拟副本。
     *
     * @param originalId 原始图像 ID
     * @return 虚拟副本信息列表
     */
    fun listVirtualCopies(originalId: String): List<VirtualCopyInfo> {
        // 优先从缓存读取
        cache[originalId]?.let { return it.toList() }

        // 从磁盘加载
        val copies = loadVirtualCopiesForOriginal(originalId)
        cache[originalId] = copies.toMutableList()
        return copies
    }

    /**
     * 删除虚拟副本。
     *
     * @param copyId 要删除的副本 ID
     * @return 是否删除成功
     */
    fun deleteVirtualCopy(copyId: String): Boolean {
        val info = findVirtualCopyInfo(copyId) ?: return false

        // 删除磁盘文件
        val copyDir = File(copiesDir, copyId)
        if (copyDir.exists()) {
            copyDir.deleteRecursively()
        }

        // 更新缓存
        cache[info.originalId]?.removeAll { it.id == copyId }
        historyCache.remove(copyId)

        Log.d(TAG, "Deleted virtual copy '$copyId'")
        return true
    }

    /**
     * 将虚拟副本提升为主版本。
     * 使该副本成为当前活跃的编辑历史。
     *
     * @param copyId 要提升的副本 ID
     * @return 副本的 BranchableHistory，失败返回 null
     */
    fun promoteVirtualCopy(copyId: String): BranchableHistory? {
        val info = findVirtualCopyInfo(copyId) ?: return null

        // 加载副本历史
        val history = loadVirtualCopyHistory(copyId) ?: run {
            Log.w(TAG, "Failed to load history for virtual copy '$copyId'")
            return null
        }

        // 切换到副本的主分支
        history.switchBranch("main")

        Log.d(TAG, "Promoted virtual copy '$copyId' (${info.name}) to primary version")
        return history
    }

    /**
     * 比较两个虚拟副本，返回差异描述列表。
     * 逐项比较 Adjustments 字段，记录有差异的字段名。
     *
     * @param copyId1 第一个副本 ID
     * @param copyId2 第二个副本 ID
     * @return 差异描述列表，如 ["exposure: +0.5", "contrast: -10"]
     */
    fun compareVirtualCopies(copyId1: String, copyId2: String): List<String> {
        val history1 = loadVirtualCopyHistory(copyId1) ?: return listOf("Error: copy '$copyId1' not found")
        val history2 = loadVirtualCopyHistory(copyId2) ?: return listOf("Error: copy '$copyId2' not found")

        val adj1 = history1.currentNode.adjustments
        val adj2 = history2.currentNode.adjustments

        val differences = mutableListOf<String>()

        // 比较所有 Adjustments 字段
        fun compareField(name: String, v1: Float, v2: Float) {
            if (v1 != v2) {
                val diff = v2 - v1
                val sign = if (diff >= 0) "+" else ""
                differences.add("$name: $sign${"%.2f".format(diff)}")
            }
        }

        compareField("exposure", adj1.exposure, adj2.exposure)
        compareField("contrast", adj1.contrast, adj2.contrast)
        compareField("highlights", adj1.highlights, adj2.highlights)
        compareField("shadows", adj1.shadows, adj2.shadows)
        compareField("whites", adj1.whites, adj2.whites)
        compareField("blacks", adj1.blacks, adj2.blacks)
        compareField("vibrance", adj1.vibrance, adj2.vibrance)
        compareField("saturation", adj1.saturation, adj2.saturation)
        compareField("temperature", adj1.temperature, adj2.temperature)
        compareField("tint", adj1.tint, adj2.tint)
        compareField("sharpness", adj1.sharpness, adj2.sharpness)
        compareField("toneLevel", adj1.toneLevel, adj2.toneLevel)
        compareField("filmIntensity", adj1.filmIntensity, adj2.filmIntensity)

        return if (differences.isEmpty()) listOf("No differences") else differences
    }

    /**
     * 将一个虚拟副本的调整合并到另一个副本。
     * 将 source 副本的 Adjustments 作为新节点追加到 target 副本的历史中。
     *
     * @param sourceCopyId 源副本 ID（其调整将被合并）
     * @param targetCopyId 目标副本 ID（接收调整）
     * @return 合并后的新节点，失败返回 null
     */
    fun mergeVirtualCopy(sourceCopyId: String, targetCopyId: String): HistoryNode? {
        val sourceHistory = loadVirtualCopyHistory(sourceCopyId) ?: run {
            Log.w(TAG, "Source virtual copy '$sourceCopyId' not found")
            return null
        }
        val targetHistory = loadVirtualCopyHistory(targetCopyId) ?: run {
            Log.w(TAG, "Target virtual copy '$targetCopyId' not found")
            return null
        }

        val sourceAdjustments = sourceHistory.currentNode.adjustments
        val mergedNode = targetHistory.pushState(
            adjustments = sourceAdjustments,
            label = "合并自副本: $sourceCopyId",
        )

        // 持久化合并后的目标副本
        val targetInfo = findVirtualCopyInfo(targetCopyId)
        if (targetInfo != null) {
            saveVirtualCopy(targetCopyId, targetInfo, targetHistory)
        }

        Log.d(TAG, "Merged virtual copy '$sourceCopyId' into '$targetCopyId'")
        return mergedNode
    }

    /**
     * 获取虚拟副本对应的原始图像 ID。
     *
     * @param copyId 虚拟副本 ID
     * @return 原始图像 ID，失败返回 null
     */
    fun getOriginalId(copyId: String): String? {
        return findVirtualCopyInfo(copyId)?.originalId
    }

    /**
     * 获取虚拟副本的编辑历史。
     *
     * @param copyId 虚拟副本 ID
     * @return BranchableHistory，失败返回 null
     */
    fun getHistory(copyId: String): BranchableHistory? {
        return historyCache[copyId] ?: loadVirtualCopyHistory(copyId)?.also {
            historyCache[copyId] = it
        }
    }

    /**
     * 设置虚拟副本的缩略图路径。
     *
     * @param copyId 虚拟副本 ID
     * @param thumbnailPath 缩略图路径
     */
    fun setThumbnail(copyId: String, thumbnailPath: String) {
        val info = findVirtualCopyInfo(copyId) ?: return
        val updated = info.copy(thumbnailPath = thumbnailPath)
        // 更新缓存
        cache[info.originalId]?.replaceAll { if (it.id == copyId) updated else it }
        // 更新持久化
        val history = getHistory(copyId) ?: return
        saveVirtualCopy(copyId, updated, history)
    }

    // ── 持久化 ────────────────────────────────────────────────

    /**
     * 保存虚拟副本到磁盘。
     */
    private fun saveVirtualCopy(copyId: String, info: VirtualCopyInfo, history: BranchableHistory) {
        val copyDir = File(copiesDir, copyId)
        if (!copyDir.exists()) copyDir.mkdirs()

        try {
            // 保存元数据 JSON
            val metadataFile = File(copyDir, METADATA_FILE)
            val json = JSONObject().apply {
                put("id", info.id)
                put("name", info.name)
                put("originalId", info.originalId)
                put("createdAt", info.createdAt)
                put("adjustmentCount", info.adjustmentCount)
                put("thumbnailPath", info.thumbnailPath)
            }
            metadataFile.writeText(json.toString(2))

            // 保存历史数据 JSON（简化版序列化）
            val historyFile = File(copyDir, "history.json")
            val historyJson = serializeHistoryToJson(history)
            historyFile.writeText(historyJson.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save virtual copy '$copyId': ${e.message}")
        }
    }

    /**
     * 从磁盘加载指定原始图像的所有虚拟副本信息。
     */
    private fun loadVirtualCopiesForOriginal(originalId: String): List<VirtualCopyInfo> {
        val result = mutableListOf<VirtualCopyInfo>()
        val dirs = copiesDir.listFiles { f -> f.isDirectory } ?: return result

        for (dir in dirs) {
            val metadataFile = File(dir, METADATA_FILE)
            if (!metadataFile.exists()) continue

            try {
                val json = JSONObject(metadataFile.readText())
                if (json.optString("originalId") == originalId) {
                    result.add(
                        VirtualCopyInfo(
                            id = json.getString("id"),
                            name = json.getString("name"),
                            originalId = json.getString("originalId"),
                            createdAt = json.getLong("createdAt"),
                            adjustmentCount = json.optInt("adjustmentCount", 0),
                            thumbnailPath = json.optString("thumbnailPath", ""),
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load metadata from ${dir.name}: ${e.message}")
            }
        }

        return result
    }

    /**
     * 从磁盘加载虚拟副本的编辑历史。
     */
    private fun loadVirtualCopyHistory(copyId: String): BranchableHistory? {
        val copyDir = File(copiesDir, copyId)
        val historyFile = File(copyDir, "history.json")
        if (!historyFile.exists()) return null

        return try {
            val json = JSONObject(historyFile.readText())
            deserializeHistoryFromJson(json)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load history for '$copyId': ${e.message}")
            null
        }
    }

    /**
     * 在内存缓存和所有已加载的原始图像中查找虚拟副本信息。
     */
    private fun findVirtualCopyInfo(copyId: String): VirtualCopyInfo? {
        // 搜索缓存
        for ((_, list) in cache) {
            val found = list.find { it.id == copyId }
            if (found != null) return found
        }
        return null
    }

    // ── JSON 序列化/反序列化 ──────────────────────────────────

    /**
     * 将 BranchableHistory 序列化为 JSONObject。
     */
    private fun serializeHistoryToJson(history: BranchableHistory): JSONObject {
        val json = JSONObject()
        json.put("currentNodeId", history.currentNodeId)
        json.put("currentBranchName", history.currentBranchName)

        // 序列化所有节点
        val nodesArray = JSONArray()
        for (node in history.getAllNodes()) {
            val nodeJson = JSONObject().apply {
                put("id", node.id)
                put("parentId", node.parentId?.let { it } ?: JSONObject.NULL)
                put("branchName", node.branchName)
                put("label", node.label)
                put("timestamp", node.timestamp)
                put("contentHash", node.contentHash)
                // 序列化 Adjustments
                put("adjustments", serializeAdjustments(node.adjustments))
                // 子节点 ID 列表
                put("childrenIds", JSONArray(node.childrenIds))
            }
            nodesArray.put(nodeJson)
        }
        json.put("nodes", nodesArray)

        // 分支映射
        json.put("branchTips", JSONObject(history.branchTips))
        json.put("branchRoots", JSONObject(history.branchRoots))

        return json
    }

    /**
     * 从 JSONObject 反序列化 BranchableHistory。
     */
    private fun deserializeHistoryFromJson(json: JSONObject): BranchableHistory? {
        return try {
            val currentNodeId = json.getString("currentNodeId")
            val currentBranchName = json.getString("currentBranchName")

            val nodesArray = json.getJSONArray("nodes")
            val nodeMap = mutableMapOf<String, HistoryNode>()
            var rootNode: HistoryNode? = null

            // 第一遍：创建所有节点（不含子节点引用）
            val tempNodes = mutableListOf<Pair<HistoryNode, JSONArray>>()
            for (i in 0 until nodesArray.length()) {
                val nodeJson = nodesArray.getJSONObject(i)
                val adj = deserializeAdjustments(nodeJson.getJSONObject("adjustments"))
                val parentId = if (nodeJson.isNull("parentId")) null else nodeJson.getString("parentId")
                val node = HistoryNode(
                    id = nodeJson.getString("id"),
                    parentId = parentId,
                    adjustments = adj,
                    label = nodeJson.optString("label", ""),
                    timestamp = nodeJson.optLong("timestamp", System.currentTimeMillis()),
                    branchName = nodeJson.optString("branchName", "main"),
                    contentHash = nodeJson.optString("contentHash", ""),
                )
                nodeMap[node.id] = node
                if (parentId == null) rootNode = node
                tempNodes.add(Pair(node, nodeJson.getJSONArray("childrenIds")))
            }

            // 第二遍：填充子节点引用
            for ((node, childrenIds) in tempNodes) {
                for (j in 0 until childrenIds.length()) {
                    node.childrenIds.add(childrenIds.getString(j))
                }
            }

            if (rootNode == null) {
                Log.w(TAG, "Deserialized history has no root node")
                return null
            }

            val branchTips = mutableMapOf<String, String>()
            val tipsObj = json.getJSONObject("branchTips")
            for (key in tipsObj.keys()) {
                branchTips[key] = tipsObj.getString(key)
            }

            val branchRoots = mutableMapOf<String, String>()
            val rootsObj = json.getJSONObject("branchRoots")
            for (key in rootsObj.keys()) {
                branchRoots[key] = rootsObj.getString(key)
            }

            BranchableHistory(
                root = rootNode,
                nodeMap = nodeMap,
                branchTips = branchTips,
                branchRoots = branchRoots,
                currentNodeId = currentNodeId,
                currentBranchName = currentBranchName,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deserialize history: ${e.message}")
            null
        }
    }

    /**
     * 序列化 Adjustments 为 JSONObject。
     */
    private fun serializeAdjustments(adj: Adjustments): JSONObject {
        return JSONObject().apply {
            put("exposure", adj.exposure)
            put("toneLevel", adj.toneLevel)
            put("filmIntensity", adj.filmIntensity)
            put("contrast", adj.contrast)
            put("highlights", adj.highlights)
            put("shadows", adj.shadows)
            put("whites", adj.whites)
            put("blacks", adj.blacks)
            put("vibrance", adj.vibrance)
            put("saturation", adj.saturation)
            put("temperature", adj.temperature)
            put("tint", adj.tint)
            put("sharpness", adj.sharpness)
            // HSL
            put("hslReds", serializeHSL(adj.hslReds))
            put("hslOranges", serializeHSL(adj.hslOranges))
            put("hslYellows", serializeHSL(adj.hslYellows))
            put("hslGreens", serializeHSL(adj.hslGreens))
            put("hslAquas", serializeHSL(adj.hslAquas))
            put("hslBlues", serializeHSL(adj.hslBlues))
            put("hslPurples", serializeHSL(adj.hslPurples))
            put("hslMagentas", serializeHSL(adj.hslMagentas))
            // Oklab
            put("oklabHueShift", adj.oklabHueShift)
            put("oklabSaturation", adj.oklabSaturation)
            put("oklabChroma", adj.oklabChroma)
            put("oklabLightness", adj.oklabLightness)
            put("oklabContrast", adj.oklabContrast)
            put("oklabTextureAmount", adj.oklabTextureAmount)
        }
    }

    /**
     * 从 JSONObject 反序列化 Adjustments。
     */
    private fun deserializeAdjustments(json: JSONObject): Adjustments {
        return Adjustments(
            exposure = json.optDouble("exposure", 0.0).toFloat(),
            toneLevel = json.optDouble("toneLevel", 0.0).toFloat(),
            filmIntensity = json.optDouble("filmIntensity", 0.0).toFloat(),
            contrast = json.optDouble("contrast", 0.0).toFloat(),
            highlights = json.optDouble("highlights", 0.0).toFloat(),
            shadows = json.optDouble("shadows", 0.0).toFloat(),
            whites = json.optDouble("whites", 0.0).toFloat(),
            blacks = json.optDouble("blacks", 0.0).toFloat(),
            vibrance = json.optDouble("vibrance", 0.0).toFloat(),
            saturation = json.optDouble("saturation", 0.0).toFloat(),
            temperature = json.optDouble("temperature", 0.0).toFloat(),
            tint = json.optDouble("tint", 0.0).toFloat(),
            sharpness = json.optDouble("sharpness", 0.0).toFloat(),
            hslReds = deserializeHSL(json.optJSONObject("hslReds")),
            hslOranges = deserializeHSL(json.optJSONObject("hslOranges")),
            hslYellows = deserializeHSL(json.optJSONObject("hslYellows")),
            hslGreens = deserializeHSL(json.optJSONObject("hslGreens")),
            hslAquas = deserializeHSL(json.optJSONObject("hslAquas")),
            hslBlues = deserializeHSL(json.optJSONObject("hslBlues")),
            hslPurples = deserializeHSL(json.optJSONObject("hslPurples")),
            hslMagentas = deserializeHSL(json.optJSONObject("hslMagentas")),
            oklabHueShift = json.optDouble("oklabHueShift", 0.0).toFloat(),
            oklabSaturation = json.optDouble("oklabSaturation", 0.0).toFloat(),
            oklabChroma = json.optDouble("oklabChroma", 0.0).toFloat(),
            oklabLightness = json.optDouble("oklabLightness", 0.0).toFloat(),
            oklabContrast = json.optDouble("oklabContrast", 0.0).toFloat(),
            oklabTextureAmount = json.optDouble("oklabTextureAmount", 0.0).toFloat(),
        )
    }

    private fun serializeHSL(hsl: Adjustments.HSL): JSONObject {
        return JSONObject().apply {
            put("hue", hsl.hue)
            put("saturation", hsl.saturation)
            put("luminance", hsl.luminance)
        }
    }

    private fun deserializeHSL(json: JSONObject?): Adjustments.HSL {
        if (json == null) return Adjustments.HSL()
        return Adjustments.HSL(
            hue = json.optDouble("hue", 0.0).toFloat(),
            saturation = json.optDouble("saturation", 0.0).toFloat(),
            luminance = json.optDouble("luminance", 0.0).toFloat(),
        )
    }
}
