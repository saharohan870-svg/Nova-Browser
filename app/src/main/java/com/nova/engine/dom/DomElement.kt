package com.nova.engine.dom

import com.nova.engine.css.CssElement
import com.nova.engine.media.AccessibleElement

/** Lightweight DOM element used by Nova Engine's early document pipeline. */
class DomElement(
    override val tagName: String,
    attributes: Map<String, String> = emptyMap()
) : CssElement, AccessibleElement {
    private val attributeStore = linkedMapOf<String, String>().apply {
        attributes.forEach { (key, value) -> put(key.lowercase(), value) }
    }
    private val directText = StringBuilder()
    private val childElements = mutableListOf<DomElement>()
    private var parentElement: DomElement? = null

    override val id: String get() = attribute("id").orEmpty()
    override val classes: Set<String>
        get() = attribute("class").orEmpty().split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
    override val parent: CssElement? get() = parentElement
    override val attributes: Map<String, String> get() = attributeStore.toMap()
    override val children: List<AccessibleElement> get() = childElements.toList()
    val elementChildren: List<DomElement> get() = childElements.toList()
    val parentNode: DomElement? get() = parentElement

    override val textContent: String
        get() = buildString {
            append(directText)
            childElements.forEach { append(it.textContent) }
        }

    fun attribute(name: String): String? = attributeStore[name.lowercase()]
    fun hasAttribute(name: String): Boolean = attributeStore.containsKey(name.lowercase())
    fun setAttribute(name: String, value: String) { attributeStore[name.lowercase()] = value }
    fun appendText(text: String) { directText.append(text) }

    fun appendChild(child: DomElement) {
        child.parentElement?.childElements?.remove(child)
        child.parentElement = this
        childElements += child
    }

    fun descendants(includeSelf: Boolean = false): Sequence<DomElement> = sequence {
        if (includeSelf) yield(this@DomElement)
        for (child in childElements) {
            yield(child)
            yieldAll(child.descendants())
        }
    }

    fun elementsByTag(tag: String): List<DomElement> =
        descendants(includeSelf = true).filter { it.tagName.equals(tag, ignoreCase = true) }.toList()
}
