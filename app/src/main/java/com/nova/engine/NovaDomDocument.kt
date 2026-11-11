package com.zorvi.engine

/**
 * A small DOM-like tree and mutation/event model for Zorvi Engine.
 *
 * This is an internal prototype API, not a complete WHATWG DOM implementation.
 * JavaScript bindings and standards-compliant event propagation are later work.
 */
class ZorviDomNode internal constructor(
    val tagName: String,
    var textContent: String = "",
    val attributes: MutableMap<String, String> = mutableMapOf(),
    val children: MutableList<ZorviDomNode> = mutableListOf()
) {
    var parent: ZorviDomNode? = null
        private set

    fun appendChild(child: ZorviDomNode) {
        child.parent?.children?.remove(child)
        child.parent = this
        children.add(child)
    }

    fun removeChild(child: ZorviDomNode): Boolean {
        val removed = children.remove(child)
        if (removed) child.parent = null
        return removed
    }

    fun getAttribute(name: String): String? = attributes[name.lowercase()]
    fun setAttribute(name: String, value: String) { attributes[name.lowercase()] = value }
    fun removeAttribute(name: String) { attributes.remove(name.lowercase()) }

    fun text(): String = if (tagName == "#text") textContent else
        buildString { append(textContent); children.forEach { append(it.text()) } }

    fun descendants(): Sequence<ZorviDomNode> = sequence {
        for (child in children) {
            yield(child)
            yieldAll(child.descendants())
        }
    }

    fun findById(id: String): ZorviDomNode? =
        if (attributes["id"] == id) this else descendants().firstOrNull { it.attributes["id"] == id }

    fun findByTag(tag: String): List<ZorviDomNode> =
        descendants().filter { it.tagName.equals(tag, ignoreCase = true) }.toList()
}

class ZorviDomDocument internal constructor(val documentElement: ZorviDomNode) {
    private val listeners = mutableMapOf<String, MutableList<(ZorviDomEvent) -> Unit>>()

    fun getElementById(id: String): ZorviDomNode? = documentElement.findById(id)
    fun getElementsByTagName(tag: String): List<ZorviDomNode> =
        if (documentElement.tagName.equals(tag, true)) listOf(documentElement) else documentElement.findByTag(tag)

    fun addEventListener(type: String, listener: (ZorviDomEvent) -> Unit) {
        listeners.getOrPut(type.lowercase()) { mutableListOf() }.add(listener)
    }

    internal fun dispatch(type: String, target: ZorviDomNode) {
        val event = ZorviDomEvent(type.lowercase(), target)
        listeners[type.lowercase()]?.toList()?.forEach { it(event) }
    }

    companion object {
        fun from(document: HtmlDocument): ZorviDomDocument {
            fun convert(node: HtmlNode): ZorviDomNode {
                val result = ZorviDomNode(node.tag, node.text, node.attributes.toMutableMap())
                node.children.forEach { result.appendChild(convert(it)) }
                return result
            }
            return ZorviDomDocument(convert(document.root))
        }
    }
}

data class ZorviDomEvent(val type: String, val target: ZorviDomNode)
