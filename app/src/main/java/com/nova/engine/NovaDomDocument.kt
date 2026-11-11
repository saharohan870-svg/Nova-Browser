package com.nova.engine

/**
 * A small DOM-like tree and mutation/event model for Nova Engine.
 *
 * This is an internal prototype API, not a complete WHATWG DOM implementation.
 * JavaScript bindings and standards-compliant event propagation are later work.
 */
class NovaDomNode internal constructor(
    val tagName: String,
    var textContent: String = "",
    val attributes: MutableMap<String, String> = mutableMapOf(),
    val children: MutableList<NovaDomNode> = mutableListOf()
) {
    var parent: NovaDomNode? = null
        private set

    fun appendChild(child: NovaDomNode) {
        child.parent?.children?.remove(child)
        child.parent = this
        children.add(child)
    }

    fun removeChild(child: NovaDomNode): Boolean {
        val removed = children.remove(child)
        if (removed) child.parent = null
        return removed
    }

    fun getAttribute(name: String): String? = attributes[name.lowercase()]
    fun setAttribute(name: String, value: String) { attributes[name.lowercase()] = value }
    fun removeAttribute(name: String) { attributes.remove(name.lowercase()) }

    fun text(): String = if (tagName == "#text") textContent else
        buildString { append(textContent); children.forEach { append(it.text()) } }

    fun descendants(): Sequence<NovaDomNode> = sequence {
        for (child in children) {
            yield(child)
            yieldAll(child.descendants())
        }
    }

    fun findById(id: String): NovaDomNode? =
        if (attributes["id"] == id) this else descendants().firstOrNull { it.attributes["id"] == id }

    fun findByTag(tag: String): List<NovaDomNode> =
        descendants().filter { it.tagName.equals(tag, ignoreCase = true) }.toList()
}

class NovaDomDocument internal constructor(val documentElement: NovaDomNode) {
    private val listeners = mutableMapOf<String, MutableList<(NovaDomEvent) -> Unit>>()

    fun getElementById(id: String): NovaDomNode? = documentElement.findById(id)
    fun getElementsByTagName(tag: String): List<NovaDomNode> =
        if (documentElement.tagName.equals(tag, true)) listOf(documentElement) else documentElement.findByTag(tag)

    fun addEventListener(type: String, listener: (NovaDomEvent) -> Unit) {
        listeners.getOrPut(type.lowercase()) { mutableListOf() }.add(listener)
    }

    internal fun dispatch(type: String, target: NovaDomNode) {
        val event = NovaDomEvent(type.lowercase(), target)
        listeners[type.lowercase()]?.toList()?.forEach { it(event) }
    }

    companion object {
        fun from(document: HtmlDocument): NovaDomDocument {
            fun convert(node: HtmlNode): NovaDomNode {
                val result = NovaDomNode(node.tag, node.text, node.attributes.toMutableMap())
                node.children.forEach { result.appendChild(convert(it)) }
                return result
            }
            return NovaDomDocument(convert(document.root))
        }
    }
}

data class NovaDomEvent(val type: String, val target: NovaDomNode)
