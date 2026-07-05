package com.alcedo.studio.core

import com.alcedo.studio.data.model.Adjustments
import com.alcedo.studio.data.model.EditHistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class BranchableHistory {

    private val _history = MutableStateFlow<List<HistoryNode>>(emptyList())
    val history: StateFlow<List<HistoryNode>> = _history.asStateFlow()

    private val _currentNodeId = MutableStateFlow<String?>(null)
    val currentNodeId: StateFlow<String?> = _currentNodeId.asStateFlow()

    private val _branches = MutableStateFlow<List<HistoryBranch>>(emptyList())
    val branches: StateFlow<List<HistoryBranch>> = _branches.asStateFlow()

    private val _currentBranchId = MutableStateFlow<String>("main")
    val currentBranchId: StateFlow<String> = _currentBranchId.asStateFlow()

    private val maxHistorySize = 50

    init {
        val mainBranch = HistoryBranch(
            id = "main",
            name = "主版本",
            createdAt = System.currentTimeMillis()
        )
        _branches.value = listOf(mainBranch)
    }

    fun addEntry(description: String, adjustments: Adjustments) {
        val currentBranches = _branches.value
        val currentBranchId = _currentBranchId.value
        val currentNodeId = _currentNodeId.value
        val currentHistory = _history.value

        val newNode = HistoryNode(
            id = UUID.randomUUID().toString(),
            branchId = currentBranchId,
            description = description,
            adjustments = adjustments,
            parentId = currentNodeId,
            timestamp = System.currentTimeMillis(),
            childrenIds = emptyList()
        )

        val updatedHistory = currentHistory.toMutableList()

        if (currentNodeId != null) {
            val parentIndex = updatedHistory.indexOfFirst { it.id == currentNodeId }
            if (parentIndex >= 0) {
                val parent = updatedHistory[parentIndex]
                updatedHistory[parentIndex] = parent.copy(
                    childrenIds = parent.childrenIds + newNode.id
                )
            }
        }

        updatedHistory.add(newNode)

        if (updatedHistory.size > maxHistorySize) {
            val toRemove = updatedHistory.size - maxHistorySize
            val rootNodes = updatedHistory.filter { it.parentId == null }
            if (rootNodes.isNotEmpty()) {
                val removeIds = mutableSetOf<String>()
                var currentId = rootNodes.firstOrNull()?.id
                var count = 0
                while (currentId != null && count < toRemove) {
                    removeIds.add(currentId)
                    count++
                    val node = updatedHistory.find { it.id == currentId }
                    currentId = node?.childrenIds?.firstOrNull()
                }
                updatedHistory.removeAll { node -> removeIds.contains(node.id) }
            }
        }

        _history.value = updatedHistory
        _currentNodeId.value = newNode.id
    }

    fun undo(): Adjustments? {
        val currentId = _currentNodeId.value ?: return null
        val history = _history.value

        val currentNode = history.find { it.id == currentId } ?: return null
        val parentNode = currentNode.parentId?.let { pid -> history.find { it.id == pid } }

        if (parentNode != null) {
            _currentNodeId.value = parentNode.id
            return parentNode.adjustments
        }

        return null
    }

    fun redo(): Adjustments? {
        val currentId = _currentNodeId.value
        val history = _history.value

        if (currentId == null) {
            val firstNode = history.firstOrNull()
            if (firstNode != null) {
                _currentNodeId.value = firstNode.id
                return firstNode.adjustments
            }
            return null
        }

        val currentNode = history.find { it.id == currentId } ?: return null
        val firstChild = currentNode.childrenIds.firstOrNull()?.let { cid ->
            history.find { it.id == cid }
        }

        if (firstChild != null) {
            _currentNodeId.value = firstChild.id
            return firstChild.adjustments
        }

        return null
    }

    fun canUndo(): Boolean {
        val currentId = _currentNodeId.value ?: return false
        val history = _history.value
        val currentNode = history.find { it.id == currentId } ?: return false
        return currentNode.parentId != null
    }

    fun canRedo(): Boolean {
        val currentId = _currentNodeId.value
        val history = _history.value

        if (currentId == null) {
            return history.isNotEmpty()
        }

        val currentNode = history.find { it.id == currentId } ?: return false
        return currentNode.childrenIds.isNotEmpty()
    }

    fun createBranch(name: String): String {
        val currentId = _currentNodeId.value ?: return ""
        val newBranchId = UUID.randomUUID().toString()
        val currentBranchId = _currentBranchId.value

        val newBranch = HistoryBranch(
            id = newBranchId,
            name = name,
            parentBranchId = currentBranchId,
            parentNodeId = currentId,
            createdAt = System.currentTimeMillis()
        )

        _branches.value = _branches.value + newBranch
        _currentBranchId.value = newBranchId

        return newBranchId
    }

    fun switchBranch(branchId: String) {
        val branch = _branches.value.find { it.id == branchId } ?: return
        _currentBranchId.value = branchId

        if (branch.parentNodeId != null) {
            _currentNodeId.value = branch.parentNodeId
        }
    }

    fun deleteBranch(branchId: String) {
        if (branchId == "main") return

        val history = _history.value
        val branchNodes = history.filter { it.branchId == branchId }
        val idsToRemove = branchNodes.map { it.id }.toSet()

        _history.value = history.filter { !idsToRemove.contains(it.id) }
        _branches.value = _branches.value.filter { it.id != branchId }

        if (_currentBranchId.value == branchId) {
            val branch = _branches.value.find { it.id == branchId }
            val parentBranchId = branch?.parentBranchId ?: "main"
            _currentBranchId.value = parentBranchId
        }
    }

    fun getCurrentBranchName(): String {
        return _branches.value.find { it.id == _currentBranchId.value }?.name ?: "主版本"
    }

    fun getBranchList(): List<HistoryBranch> = _branches.value

    fun getCurrentHistoryList(): List<EditHistoryEntry> {
        val result = mutableListOf<EditHistoryEntry>()
        var currentId = _currentNodeId.value
        val history = _history.value

        while (currentId != null) {
            val node = history.find { it.id == currentId } ?: break
            result.add(
                0, EditHistoryEntry(
                    id = node.id,
                    timestamp = node.timestamp,
                    description = node.description,
                    adjustments = node.adjustments
                )
            )
            currentId = node.parentId
        }

        return result
    }

    fun jumpToEntry(entryId: String): Adjustments? {
        val node = _history.value.find { it.id == entryId } ?: return null
        _currentNodeId.value = node.id
        return node.adjustments
    }

    fun clear() {
        _history.value = emptyList()
        _currentNodeId.value = null
        _branches.value = listOf(
            HistoryBranch(
                id = "main",
                name = "主版本",
                createdAt = System.currentTimeMillis()
            )
        )
        _currentBranchId.value = "main"
    }
}

data class HistoryNode(
    val id: String,
    val branchId: String,
    val description: String,
    val adjustments: Adjustments,
    val parentId: String?,
    val timestamp: Long,
    val childrenIds: List<String>
)

data class HistoryBranch(
    val id: String,
    val name: String,
    val parentBranchId: String? = null,
    val parentNodeId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
