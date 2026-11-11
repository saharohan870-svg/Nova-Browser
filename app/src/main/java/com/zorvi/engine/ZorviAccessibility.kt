package com.zorvi.engine

/**
 * A small semantic snapshot for the experimental renderer's Android
 * accessibility announcement. It is not a complete browser accessibility tree.
 */
internal data class ZorviAccessibleNode(
    val role: String,
    val label: String,
    val hint: String = "",
    val enabled: Boolean = true,
    val headingLevel: Int? = null
)

internal data class ZorviAccessibilityTree(val nodes: List<ZorviAccessibleNode>) {
    companion object {
        private val ignoredTags = setOf("head", "script", "style", "noscript", "title")

        fun from(document: HtmlDocument): ZorviAccessibilityTree {
            val all = mutableListOf<HtmlNode>()
            fun collect(node: HtmlNode) {
                if (node.tag in ignoredTags || node.attributes.containsKey("hidden") ||
                    node.attributes["aria-hidden"].equals("true", ignoreCase = true)) return
                all += node
                node.children.forEach(::collect)
            }
            collect(document.root)

            fun text(node: HtmlNode): String = buildString {
                fun appendText(current: HtmlNode) {
                    if (current.tag == "#text") append(current.text)
                    else current.children.forEach(::appendText)
                }
                appendText(node)
            }.trim().replace(Regex("\\s+"), " ")

            fun labelFor(node: HtmlNode): String {
                val attrs = node.attributes
                attrs["aria-label"]?.takeIf { it.isNotBlank() }?.let { return it.trim() }
                attrs["alt"]?.takeIf { it.isNotBlank() }?.let { return it.trim() }
                val id = attrs["id"]
                if (!id.isNullOrBlank()) {
                    val label = all.firstOrNull {
                        it.tag == "label" && it.attributes["for"] == id
                    }?.let(::text)
                    if (!label.isNullOrBlank()) return label
                }
                attrs["placeholder"]?.takeIf { it.isNotBlank() }?.let { return it.trim() }
                attrs["title"]?.takeIf { it.isNotBlank() }?.let { return it.trim() }
                return text(node)
            }

            val nodes = all.mapNotNull { node ->
                val tag = node.tag
                val attrs = node.attributes
                val role = when {
                    tag.matches(Regex("h[1-6]")) -> "heading"
                    tag == "a" && !attrs["href"].isNullOrBlank() -> "link"
                    tag == "button" -> "button"
                    tag == "textarea" -> "text-field"
                    tag == "select" -> "combo-box"
                    tag == "input" -> when (attrs["type"]?.lowercase()) {
                        "button", "submit", "reset" -> "button"
                        "checkbox" -> "checkbox"
                        "radio" -> "radio-button"
                        "range" -> "slider"
                        "number" -> "spin-button"
                        "hidden" -> null
                        else -> "text-field"
                    }
                    tag == "img" -> "image"
                    tag == "nav" -> "navigation"
                    tag == "main" -> "main"
                    tag == "form" -> "form"
                    tag == "button" -> "button"
                    else -> null
                } ?: return@mapNotNull null
                val label = labelFor(node)
                if (label.isBlank() && role !in setOf("form", "main", "navigation")) return@mapNotNull null
                val headingLevel = if (role == "heading") tag.last().digitToIntOrNull() else null
                ZorviAccessibleNode(
                    role = attrs["role"]?.takeIf { it.isNotBlank() } ?: role,
                    label = label,
                    hint = attrs["aria-describedby"].orEmpty(),
                    enabled = "disabled" !in attrs && attrs["aria-disabled"] != "true",
                    headingLevel = headingLevel
                )
            }
            return ZorviAccessibilityTree(nodes)
        }
    }
}
