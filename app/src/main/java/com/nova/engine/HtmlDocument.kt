package com.nova.engine

/**
 * Tiny first-pass HTML tree builder for Nova Engine.
 *
 * This intentionally supports a small safe subset and is not an HTML5 parser.
 * It never executes script contents. Unknown tags are retained as generic
 * containers so their text remains readable.
 */
internal data class HtmlNode(
    val tag: String,
    val text: String = "",
    val attributes: Map<String, String> = emptyMap(),
    val children: MutableList<HtmlNode> = mutableListOf()
)

internal data class HtmlDocument(val root: HtmlNode) {
    companion object {
        private val voidTags = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")
        private val blockTags = setOf("address", "article", "blockquote", "div", "footer", "h1", "h2", "h3", "h4", "header", "li", "main", "nav", "ol", "p", "pre", "section", "ul")

        fun parse(source: String): HtmlDocument {
            val root = HtmlNode("document")
            val stack = mutableListOf(root)
            val token = Regex("(?s)<!--.*?-->|<![^>]*>|<[^>]*>|[^<]+")
            val attr = Regex("""([a-zA-Z_:][a-zA-Z0-9_:.-]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
            token.findAll(source.take(2_000_000)).forEach { match ->
                val part = match.value
                when {
                    part.startsWith("<!--") || part.startsWith("<!") -> Unit
                    part.startsWith("</") -> {
                        val name = part.drop(2).substringBefore('>').trim().substringBefore(' ').lowercase()
                        val index = stack.indexOfLast { it.tag == name }
                        if (index > 0) while (stack.size > index) stack.removeAt(stack.lastIndex)
                    }
                    part.startsWith("<") -> {
                        val inside = part.drop(1).dropLast(1).trim()
                        val name = inside.split(Regex("\\s"), limit = 2).first().trimEnd('/').lowercase()
                        if (name.isEmpty() || name.startsWith("?")) return@forEach
                        val attrs = attr.findAll(inside.drop(name.length)).associate {
                            it.groupValues[1].lowercase() to (it.groups[2]?.value ?: it.groups[3]?.value ?: it.groups[4]?.value.orEmpty())
                        }
                        val node = HtmlNode(name, attributes = attrs)
                        stack.last().children.add(node)
                        if (name !in voidTags && !inside.endsWith("/")) stack.add(node)
                    }
                    else -> {
                        val parent = stack.last()
                        if (parent.tag != "script" && parent.tag != "style") {
                            val value = decodeEntities(part)
                            if (value.isNotBlank()) parent.children.add(HtmlNode("#text", value))
                        }
                    }
                }
            }
            return HtmlDocument(root)
        }

        private fun decodeEntities(value: String): String = value
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }

    fun visibleBlocks(): List<HtmlNode> {
        val result = mutableListOf<HtmlNode>()
        fun walk(node: HtmlNode) {
            if (node.tag == "script" || node.tag == "style" || node.tag == "head" || node.tag == "noscript") return
            if (node.tag == "#text") {
                result.add(node)
                return
            }
            if (node.tag in setOf("h1", "h2", "h3", "p", "li", "a", "button", "title", "pre")) {
                val text = buildString {
                    fun collect(n: HtmlNode) {
                        if (n.tag == "#text") append(n.text)
                        else n.children.forEach(::collect)
                    }
                    collect(node)
                }.trim()
                if (text.isNotEmpty()) result.add(node.copy(text = text, children = mutableListOf()))
                return
            }
            node.children.forEach(::walk)
        }
        root.children.forEach(::walk)
        return result
    }
}
