package com.nova.engine

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Minimal user-action event dispatcher for the Nova Engine prototype.
 * This is not standards-complete DOM event propagation.
 */
internal class NovaEventTarget {
    private val handlers = mutableMapOf<String, CopyOnWriteArrayList<(NovaEngineEvent) -> Unit>>()

    fun addEventListener(type: String, handler: (NovaEngineEvent) -> Unit) {
        handlers.getOrPut(type.lowercase()) { CopyOnWriteArrayList() }.add(handler)
    }

    fun removeEventListener(type: String, handler: (NovaEngineEvent) -> Unit) {
        handlers[type.lowercase()]?.remove(handler)
    }

    fun dispatch(event: NovaEngineEvent) {
        handlers[event.type.lowercase()]?.forEach { it(event) }
    }
}

internal data class NovaEngineEvent(
    val type: String,
    val targetId: String? = null,
    val payload: String? = null
)
