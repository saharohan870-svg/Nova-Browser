package com.zorvi.engine

private val ZORVI_VOID_TAGS = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input",
    "link", "meta", "param", "source", "track", "wbr"
)

/**
 * Deliberately small DOM/event model. It is not a complete WHATWG DOM.
 * Tree ownership is maintained by appendChild/removeChild; document event
 * dispatch implements capture, target and bubble phases for this prototype.
 */
internal class ZorviDomNode internal constructor(
    val tagName: String,
    var textContent: String = "",
    val attributes: MutableMap<String, String> = mutableMapOf(),
    val children: MutableList<ZorviDomNode> = mutableListOf()
) {
    var parent: ZorviDomNode? = null
        private set

    private data class Listener(
        val callback: (ZorviDomEvent) -> Unit,
        val capture: Boolean,
        val once: Boolean
    )
    private val listeners = mutableMapOf<String, MutableList<Listener>>()

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
        for (child in children.toList()) {
            yield(child)
            yieldAll(child.descendants())
        }
    }

    fun findById(id: String): ZorviDomNode? =
        if (attributes["id"] == id) this else descendants().firstOrNull { it.attributes["id"] == id }

    fun findByTag(tag: String): List<ZorviDomNode> =
        descendants().filter { it.tagName.equals(tag, ignoreCase = true) }.toList()

    fun hasClass(className: String): Boolean =
        attributes["class"].orEmpty().split(Regex("\\s+")).any { it == className }

    fun matchesSimpleSelector(selector: String): Boolean {
        val value = selector.trim()
        if (tagName.startsWith("#")) return false
        if (value == "*") return true
        val pattern = Regex("""^(?:([a-zA-Z][a-zA-Z0-9-]*))?(?:#([\\w-]+))?((?:\\.[\\w-]+)*)$""")
        val match = pattern.matchEntire(value) ?: return false
        if (match.value.isEmpty() || match.groupValues.drop(1).all { it.isEmpty() }) return false
        val tag = match.groupValues[1]
        val id = match.groupValues[2]
        val classes = Regex("""\\.([\\w-]+)""").findAll(match.groupValues[3]).map { it.groupValues[1] }
        return (tag.isEmpty() || tag.equals(tagName, ignoreCase = true)) &&
            (id.isEmpty() || attributes["id"] == id) &&
            classes.all(::hasClass)
    }

    internal fun addListener(type: String, capture: Boolean, once: Boolean, callback: (ZorviDomEvent) -> Unit) {
        listeners.getOrPut(type.lowercase()) { mutableListOf() }.add(Listener(callback, capture, once))
    }

    internal fun removeListener(type: String, capture: Boolean, callback: (ZorviDomEvent) -> Unit) {
        val key = type.lowercase()
        listeners[key]?.removeAll { it.capture == capture && it.callback === callback }
        if (listeners[key].isNullOrEmpty()) listeners.remove(key)
    }

    internal fun invokeListeners(event: ZorviDomEvent, capture: Boolean) {
        val key = event.type
        val snapshot = listeners[key]?.filter { it.capture == capture }.orEmpty()
        for (listener in snapshot) {
            if (event.immediatePropagationStopped) break
            listener.callback(event)
            if (listener.once) listeners[key]?.remove(listener)
        }
    }
}

internal class ZorviDomDocument internal constructor(val documentElement: ZorviDomNode) {
    @Volatile var onMutation: (() -> Unit)? = null

    @Synchronized fun getElementById(id: String): ZorviDomNode? = documentElement.findById(id)
    @Synchronized fun getElementsByTagName(tag: String): List<ZorviDomNode> =
        if (tag == "*") documentElement.descendants().filterNot { it.tagName.startsWith("#") }.toList()
        else if (documentElement.tagName.equals(tag, true)) listOf(documentElement) else documentElement.findByTag(tag)

    @Synchronized fun getElementsByClassName(className: String): List<ZorviDomNode> {
        val requested = className.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (requested.isEmpty()) return emptyList()
        return sequenceOf(documentElement).plus(documentElement.descendants())
            .filter { node -> requested.all(node::hasClass) }
            .toList()
    }

    @Synchronized fun querySelector(selector: String): ZorviDomNode? =
        querySelectorAll(selector).firstOrNull()

    @Synchronized fun querySelectorAll(selector: String): List<ZorviDomNode> {
        val groups = selector.split(',').map(String::trim)
        if (groups.isEmpty() || groups.any { it.isEmpty() }) return emptyList()
        return documentElement.descendants()
            .filter { node -> groups.any(node::matchesSimpleSelector) }
            .toList()
    }

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

    @Synchronized fun serialize(): String =
        documentElement.children.joinToString("") { serializeNode(it) }

    @Synchronized fun innerHtml(node: ZorviDomNode): String =
        node.children.joinToString("") { serializeNode(it) }

    @Synchronized fun outerHtml(node: ZorviDomNode): String = serializeNode(node)

    private fun serializeNode(node: ZorviDomNode): String {
        if (node.tagName == "#text") return escapeText(node.textContent)
        val tag = node.tagName.lowercase()
        val attributes = node.attributes.entries.joinToString("") { (name, value) ->
            " ${name.lowercase()}=\"${escapeAttribute(value)}\""
        }
        val opening = "<${tag}${attributes}>"
        if (tag in ZORVI_VOID_TAGS) return opening
        return opening + escapeText(node.textContent) +
            node.children.joinToString("") { serializeNode(it) } + "</${tag}>"
    }

    private fun escapeText(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun escapeAttribute(value: String): String =
        escapeText(value).replace("\"", "&quot;")

    fun addEventListener(type: String, listener: (ZorviDomEvent) -> Unit) =
        addEventListener(type, capture = false, once = false, listener = listener)

    fun addEventListener(
        type: String,
        capture: Boolean,
        once: Boolean = false,
        listener: (ZorviDomEvent) -> Unit
    ) {
        documentElement.addListener(type, capture, once, listener)
    }

    fun removeEventListener(type: String, listener: (ZorviDomEvent) -> Unit) =
        removeEventListener(type, capture = false, listener = listener)

    fun removeEventListener(type: String, capture: Boolean, listener: (ZorviDomEvent) -> Unit) {
        documentElement.removeListener(type, capture, listener)
    }

    fun dispatch(type: String, target: ZorviDomNode, bubbles: Boolean = true, cancelable: Boolean = false): ZorviDomEvent {
        val path = mutableListOf<ZorviDomNode>()
        var current: ZorviDomNode? = target
        while (current != null) {
            path += current
            current = current.parent
        }
        val event = ZorviDomEvent(type.lowercase(), target, bubbles, cancelable)

        for (node in path.asReversed().dropLast(1)) {
            if (event.propagationStopped) break
            event.currentTarget = node
            event.eventPhase = ZorviDomEvent.CAPTURING_PHASE
            node.invokeListeners(event, capture = true)
        }

        if (!event.propagationStopped) {
            event.currentTarget = target
            event.eventPhase = ZorviDomEvent.AT_TARGET
            target.invokeListeners(event, capture = true)
            if (!event.immediatePropagationStopped) target.invokeListeners(event, capture = false)
        }

        if (bubbles && !event.propagationStopped) {
            for (node in path.drop(1)) {
                if (event.propagationStopped) break
                event.currentTarget = node
                event.eventPhase = ZorviDomEvent.BUBBLING_PHASE
                node.invokeListeners(event, capture = false)
            }
        }
        event.currentTarget = null
        event.eventPhase = ZorviDomEvent.NONE
        return event
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

internal class ZorviDomEvent(
    val type: String,
    val target: ZorviDomNode,
    val bubbles: Boolean,
    val cancelable: Boolean
) {
    var currentTarget: ZorviDomNode? = null
        internal set
    var eventPhase: Int = NONE
        internal set
    var defaultPrevented: Boolean = false
        private set
    internal var propagationStopped: Boolean = false
        private set
    internal var immediatePropagationStopped: Boolean = false
        private set

    fun preventDefault() { if (cancelable) defaultPrevented = true }
    fun stopPropagation() { propagationStopped = true }
    fun stopImmediatePropagation() {
        immediatePropagationStopped = true
        propagationStopped = true
    }

    companion object {
        const val NONE = 0
        const val CAPTURING_PHASE = 1
        const val AT_TARGET = 2
        const val BUBBLING_PHASE = 3
    }
}
