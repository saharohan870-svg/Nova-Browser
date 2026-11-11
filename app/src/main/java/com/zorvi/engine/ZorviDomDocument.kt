package com.zorvi.engine

/**
 * A small DOM-like tree and mutation/event model for Zorvi Engine.
 *
 * This is an internal prototype API, not a complete WHATWG DOM implementation.
 * The document exposes a deliberately small synchronized bridge to the JavaScript runtime; standards-compliant DOM behavior is later work.
 */
internal class ZorviDomNode internal constructor(
    val tagName: String,
    var textContent: String = "",
    val attributes: MutableMap<String, String> = mutableMapOf(),
    val children: MutableList<ZorviDomNode> = mutableListOf()
) {
    var parent: ZorviDomNode? = null
        private set

    fun appendChild(child: ZorviDomNode) {
        require(child !== this) { "A node cannot be appended to itself" }
        var ancestor: ZorviDomNode? = this
        while (ancestor != null) {
            require(ancestor !== child) { "Appending this node would create a cycle" }
            ancestor = ancestor.parent
        }
        child.parent?.removeChild(child)
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

internal class ZorviDomDocument internal constructor(val documentElement: ZorviDomNode) {
    private val listeners = mutableMapOf<String, MutableList<(ZorviDomEvent) -> Unit>>()
    @Volatile var onMutation: (() -> Unit)? = null

    @Synchronized fun getElementById(id: String): ZorviDomNode? = documentElement.findById(id)
    @Synchronized fun getElementsByTagName(tag: String): List<ZorviDomNode> =
        if (documentElement.tagName.equals(tag, true)) listOf(documentElement) else documentElement.findByTag(tag)

    @Synchronized fun hasElement(id: String): Boolean = documentElement.findById(id) != null

    @Synchronized fun getTextById(id: String): String? = documentElement.findById(id)?.text()

    @Synchronized fun setTextById(id: String, value: String) {
        val node = documentElement.findById(id) ?: return
        node.textContent = ""
        node.children.toList().forEach(node::removeChild)
        if (value.isNotEmpty()) node.appendChild(ZorviDomNode("#text", value))
        onMutation?.invoke()
    }

    @Synchronized fun getAttributeById(id: String, name: String): String? =
        documentElement.findById(id)?.getAttribute(name)

    @Synchronized fun setAttributeById(id: String, name: String, value: String) {
        val node = documentElement.findById(id) ?: return
        node.setAttribute(name, value)
        onMutation?.invoke()
    }

    @Synchronized fun visibleBlocks(): List<HtmlNode> {
        fun convert(node: ZorviDomNode): HtmlNode = HtmlNode(
            tag = node.tagName,
            text = node.textContent,
            attributes = node.attributes.toMap(),
            children = node.children.map(::convert).toMutableList()
        )
        return HtmlDocument(convert(documentElement)).visibleBlocks()
    }

    @Synchronized fun addEventListener(type: String, listener: (ZorviDomEvent) -> Unit) {
        listeners.getOrPut(type.lowercase()) { mutableListOf() }.add(listener)
    }

    internal fun dispatch(type: String, target: ZorviDomNode) {
        val callbacks = synchronized(this) { listeners[type.lowercase()]?.toList().orEmpty() }
        val event = ZorviDomEvent(type.lowercase(), target)
        callbacks.forEach { it(event) }
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

internal data class ZorviDomEvent(val type: String, val target: ZorviDomNode)
