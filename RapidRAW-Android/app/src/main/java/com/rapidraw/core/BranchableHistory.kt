package com.rapidraw.core

import com.rapidraw.data.model.Adjustments
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * 历史树中的单个节点，代表一次编辑状态快照。
 * 每个节点存储 Adjustments 快照、描述、时间戳和稳定 UUID，
 * 通过 parentId / childrenIds 构成树形结构。
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
)

/**
 * Git 风格的可分支编辑历史系统。
 * 支持在树形结构上任意历史节点创建分支、撤销/重做、合并分支等操作。
 * 线程安全（所有公开方法通过 synchronized 保护）。
 * 通过 kotlinx.serialization 支持序列化/持久化。
 * 仅存储 Adjustments 快照（差异），不存储位图，保证高效。
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
     * 由于 Kotlin data class 的 init 块会在反序列化时重新执行，
     * 我们在锁字段上使用懒初始化以确保安全。
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
            val parent = nodeMap[currentNodeId]!!
            val node = HistoryNode(
                parentId = parent.id,
                adjustments = adjustments,
                label = label,
                branchName = currentBranchName,
            )
            parent.childrenIds.add(node.id)
            nodeMap[node.id] = node
            currentNodeId = node.id
            branchTips[currentBranchName] = node.id
            return node
        }
    }

    /**
     * 撤销：回退到当前分支上的前一个节点。
     * @return 撤销后的当前节点；若无法撤销则返回 null
     */
    fun undo(): HistoryNode? {
        synchronized(lock) {
            val current = nodeMap[currentNodeId] ?: return null
            // 在当前分支上找父节点
            val parentId = current.parentId ?: return null
            val parent = nodeMap[parentId] ?: return null
            // 确保父节点属于同一分支（或回溯到分支根）
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
            sourceNode.childrenIds.add(branchRoot.id)
            nodeMap[branchRoot.id] = branchRoot
            branchTips[branchName] = branchRoot.id
            branchRoots[branchName] = branchRoot.id
            currentNodeId = branchRoot.id
            currentBranchName = branchName
            return branchRoot
        }
    }

    /**
     * 获取当前节点。
     */
    fun getCurrentNode(): HistoryNode {
        synchronized(lock) {
            return nodeMap[currentNodeId]!!
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
     * 将目标分支的所有节点合并到父分支，并删除目标分支。
     * @param branchName 要折叠的分支名称
     * @return 是否成功折叠
     */
    fun collapseBranch(branchName: String): Boolean {
        synchronized(lock) {
            if (branchName == "main") return false // 不允许折叠主分支
            val branchRootId = branchRoots[branchName] ?: return false
            val branchRoot = nodeMap[branchRootId] ?: return false
            val branchTipId = branchTips[branchName] ?: return false
            val branchTip = nodeMap[branchTipId] ?: return false

            val parentNodeId = branchRoot.parentId ?: return false
            val parentNode = nodeMap[parentNodeId] ?: return false

            // 将分支 tip 的所有子节点重新挂到分支根节点的父节点下
            for (childId in branchTip.childrenIds) {
                val child = nodeMap[childId] ?: continue
                // 更新子节点的 parentId 指向父节点
                val updatedChild = child.copy(parentId = parentNode.id, branchName = parentNode.branchName)
                nodeMap[childId] = updatedChild
                if (parentNode.id != parentNodeId) {
                    parentNode.childrenIds.add(childId)
                }
            }

            // 将分支中从 root 到 tip 的所有节点的 branchName 标记为父分支
            val branchNodes = collectBranchNodes(branchRootId)
            for (node in branchNodes) {
                val updated = node.copy(branchName = parentNode.branchName)
                nodeMap[node.id] = updated
            }

            // 从父节点的 childrenIds 中移除分支根
            parentNode.childrenIds.remove(branchRootId)

            // 删除分支映射
            branchTips.remove(branchName)
            branchRoots.remove(branchName)

            // 如果当前就在被折叠的分支上，切回父分支
            if (currentBranchName == branchName) {
                currentBranchName = parentNode.branchName
                // 将当前节点设为合并后的 tip
                val newTip = branchTip.childrenIds.firstOrNull()
                    ?: parentNode.id
                currentNodeId = newTip
            }

            return true
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
        get() = synchronized(lock) { nodeMap[currentNodeId]!! }

    /**
     * 获取所有分支名称列表。
     */
    val branchNames: List<String>
        get() = synchronized(lock) { branchTips.keys.toList() }
}

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
        return BranchableHistory(root = root)
    }
}
