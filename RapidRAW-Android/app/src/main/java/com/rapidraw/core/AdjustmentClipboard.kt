package com.rapidraw.core

import com.rapidraw.data.model.Adjustments

/**
 * Application-level clipboard for copying adjustments between Editor and Library.
 * Pure in-memory singleton — no serialization overhead.
 */
object AdjustmentClipboard {
    var adjustments: Adjustments? = null
        private set

    fun copy(adj: Adjustments) {
        adjustments = adj.copy()
    }

    fun clear() {
        adjustments = null
    }

    fun hasData(): Boolean = adjustments != null
}
