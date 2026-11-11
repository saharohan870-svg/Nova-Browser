package com.nova.engine.dom

import com.nova.engine.css.ComputedStyle
import com.nova.engine.css.CssStyleEngine
import com.nova.engine.media.AccessibilityTreeBuilder
import com.nova.engine.media.AccessibleElement
import com.nova.engine.media.AccessibleNode

/**
 * Phase 11 document integration: parsed DOM -> stylesheet cascade -> accessibility tree.
 * Produces engine data only; layout, painting and Android view integration come later.
 */
data class EngineDocument(
    val dom: DomElement,
    val styles: Map<DomElement, ComputedStyle>,
    val accessibility: AccessibleNode?
) {
    fun styleOf(element: DomElement): ComputedStyle = styles[element] ?: ComputedStyle.EMPTY
}

class NovaDocumentPipeline(
    private val parser: HtmlDomParser = HtmlDomParser()
) {
    fun process(html: String): EngineDocument {
        val dom = parser.parse(html)
        val styleEngine = CssStyleEngine()
        dom.descendants(includeSelf = true)
            .filter { it.tagName == "style" }
            .forEach { styleEngine.addStylesheet(it.textContent) }

        val styles = linkedMapOf<DomElement, ComputedStyle>()
        fun compute(element: DomElement, inherited: ComputedStyle?) {
            val cascaded = styleEngine.computeStyle(element, inherited)
            val inline = parseInlineDeclarations(element.attribute("style").orEmpty())
            val combined = if (inline.isEmpty()) cascaded else
                ComputedStyle(cascaded.asMap() + inline)
            styles[element] = combined
            element.elementChildren.forEach { compute(it, combined) }
        }
        compute(dom, null)

        val accessibilityRoot = AccessibilityTreeBuilder().build(StyledAccessibleElement(dom, styles))
        return EngineDocument(dom, styles, accessibilityRoot)
    }

    private fun parseInlineDeclarations(source: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        source.split(';').forEach { part ->
            val colon = part.indexOf(':')
            if (colon > 0) {
                val key = part.substring(0, colon).trim().lowercase()
                val value = part.substring(colon + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) result[key] = value
            }
        }
        return result
    }

    private class StyledAccessibleElement(
        private val element: DomElement,
        private val styles: Map<DomElement, ComputedStyle>
    ) : AccessibleElement {
        override val tagName: String get() = element.tagName
        override val textContent: String get() = element.textContent
        override val attributes: Map<String, String>
            get() {
                val merged = element.attributes.toMutableMap()
                if (element.tagName in setOf("head", "title", "style", "script", "meta", "link")) {
                    merged["hidden"] = ""
                }
                val style = styles[element]?.asMap().orEmpty()
                if (style.isNotEmpty()) {
                    merged["style"] = style.entries.joinToString(";") { it.key + ":" + it.value }
                }
                return merged
            }
        override val children: List<AccessibleElement>
            get() = element.elementChildren.map { StyledAccessibleElement(it, styles) }
    }
}
