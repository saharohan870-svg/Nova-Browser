package com.zorvi.engine

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Minimal user-action event dispatcher for the Zorvi Engine prototype.
 * This is not standards-complete DOM event propagation.
 */
internal class ZorviEventTarget {
    private val handlers = mutableMapOf<String, CopyOnWriteArrayList<(ZorviEngineEvent) -> Unit>>()

    fun addEventListener(type: String, handler: (ZorviEngineEvent) -> Unit) {
        handlers.getOrPut(type.lowercase()) { CopyOnWriteArrayList() }.add(handler)
    }

    fun removeEventListener(type: String, handler: (ZorviEngineEvent) -> Unit) {
        handlers[type.lowercase()]?.remove(handler)
    }

    fun dispatch(event: ZorviEngineEvent) {
        handlers[event.type.lowercase()]?.forEach { it(event) }
    }
}

internal data class ZorviEngineEvent(
    val type: String,
    val targetId: String? = null,
    val payload: String? = null
)
