package com.zorvi.engine

/**
 * Lightweight HTML document model and tokenizer/tree builder.
 *
 * This is the first standards-oriented parser milestone, not a complete WHATWG
 * HTML parser. It implements bounded tokenization, quoted/unquoted attributes,
 * comments/declarations, void elements, raw-text elements, basic implied-end-tag
 * handling, common character references and implied html/head/body containers.
 * The parser is deliberately isolated so it can later be replaced or extended
 * without changing DOM, CSS, layout or browser-shell APIs.
 */
internal data class HtmlNode(
    val tag: String,
    val text: String = "",
    val attributes: Map<String, String> = emptyMap(),
    val children: MutableList<HtmlNode> = mutableListOf()
)

internal data class HtmlDocument(val root: HtmlNode) {
    companion object {
        private const val MAX_SOURCE_CHARS = 2_000_000
        private val voidTags = setOf(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr"
        )
        private val rawTextTags = setOf("script", "style", "textarea", "title")
        private val pClosers = setOf(
            "address", "article", "aside", "blockquote", "div", "dl", "fieldset",
            "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header",
            "hr", "main", "nav", "ol", "p", "pre", "section", "table", "ul"
        )
        private val headingTags = setOf("h1", "h2", "h3", "h4", "h5", "h6")

        fun parse(source: String): HtmlDocument {
            val input = source.take(MAX_SOURCE_CHARS)
            val document = HtmlNode("document")
            val html = HtmlNode("html")
            val head = HtmlNode("head")
            val body = HtmlNode("body")
            document.children += html
            html.children += head
            html.children += body

            val stack = mutableListOf(html, head)
            var inHead = true
            var cursor = 0

            fun current(): HtmlNode = stack.lastOrNull() ?: body
            fun switchToBody() {
                inHead = false
                stack.clear()
                stack += html
                stack += body
            }
            fun appendText(value: String) {
                if (value.isEmpty()) return
                if (inHead && value.isNotBlank()) switchToBody()
                val parent = current()
                val decoded = decodeEntities(value)
                val last = parent.children.lastOrNull()
                if (last?.tag == "#text") {
                    parent.children[parent.children.lastIndex] = last.copy(text = last.text + decoded)
                } else {
                    parent.children += HtmlNode("#text", decoded)
                }
            }
            fun closeOpen(tag: String) {
                val index = stack.indexOfLast { it.tag == tag }
                if (index > 0) while (stack.size > index) stack.removeAt(stack.lastIndex)
            }
            fun closeParagraphIfNeeded(nextTag: String) {
                if (nextTag in pClosers && stack.any { it.tag == "p" }) closeOpen("p")
            }
            fun parseAttributes(sourceTag: String, from: Int): Map<String, String> {
                val result = linkedMapOf<String, String>()
                var i = from
                while (i < sourceTag.length) {
                    while (i < sourceTag.length && (sourceTag[i].isWhitespace() || sourceTag[i] == '/')) i++
                    if (i >= sourceTag.length || sourceTag[i] == '>') break
                    val start = i
                    while (i < sourceTag.length && !sourceTag[i].isWhitespace() && sourceTag[i] !in charArrayOf('=', '/', '>')) i++
                    if (i == start) { i++; continue }
                    val name = sourceTag.substring(start, i).lowercase()
                    while (i < sourceTag.length && sourceTag[i].isWhitespace()) i++
                    var value = ""
                    if (i < sourceTag.length && sourceTag[i] == '=') {
                        i++
                        while (i < sourceTag.length && sourceTag[i].isWhitespace()) i++
                        if (i < sourceTag.length && (sourceTag[i] == '"' || sourceTag[i] == '\'')) {
                            val quote = sourceTag[i++]
                            val valueStart = i
                            while (i < sourceTag.length && sourceTag[i] != quote) i++
                            value = sourceTag.substring(valueStart, i)
                            if (i < sourceTag.length) i++
                        } else {
                            val valueStart = i
                            while (i < sourceTag.length && !sourceTag[i].isWhitespace() && sourceTag[i] != '>') i++
                            value = sourceTag.substring(valueStart, i).trimEnd('/')
                        }
                    }
                    if (name.isNotEmpty() && name !in result) result[name] = decodeEntities(value)
                }
                return result
            }

            while (cursor < input.length) {
                val parent = current()
                if (parent.tag in rawTextTags) {
                    val closeStart = input.indexOf("</${parent.tag}", cursor, ignoreCase = true)
                    val end = if (closeStart < 0) input.length else closeStart
                    appendText(input.substring(cursor, end))
                    cursor = end
                    if (cursor >= input.length) break
                }

                if (input[cursor] != '<') {
                    val next = input.indexOf('<', cursor).let { if (it < 0) input.length else it }
                    appendText(input.substring(cursor, next))
                    cursor = next
                    continue
                }

                if (input.startsWith("<!--", cursor)) {
                    val end = input.indexOf("-->", cursor + 4)
                    cursor = if (end < 0) input.length else end + 3
                    continue
                }
                if (input.startsWith("<!", cursor) || input.startsWith("<?", cursor)) {
                    val end = input.indexOf('>', cursor + 2)
                    cursor = if (end < 0) input.length else end + 1
                    continue
                }

                val tagEnd = findTagEnd(input, cursor + 1)
                if (tagEnd < 0) {
                    appendText(input.substring(cursor))
                    break
                }
                val rawTag = input.substring(cursor + 1, tagEnd)
                cursor = tagEnd + 1
                val trimmed = rawTag.trim()
                if (trimmed.isEmpty()) { appendText("<>"); continue }

                if (trimmed.startsWith("/")) {
                    val name = trimmed.drop(1).takeWhile { !it.isWhitespace() && it != '/' }.lowercase()
                    if (name.isNotEmpty()) closeOpen(name)
                    if (name == "head") switchToBody()
                    continue
                }

                if (trimmed.startsWith("!") || trimmed.startsWith("?")) continue
                val name = trimmed.takeWhile { !it.isWhitespace() && it != '/' }.lowercase()
                if (name.isEmpty() || !name[0].isLetter()) {
                    appendText("<$rawTag>")
                    continue
                }
                if (name == "html" || name == "head" || name == "body") {
                    if (name == "body") switchToBody()
                    continue
                }

                val headTag = name in setOf("base", "link", "meta", "title", "style", "script", "noscript")
                if (inHead && !headTag) switchToBody()
                if (inHead && name == "title") {
                    // Title is retained in the head; the body switch occurs on </head>.
                }
                closeParagraphIfNeeded(name)
                if (name == "li") closeOpen("li")
                if (name == "dt" || name == "dd") { closeOpen("dt"); closeOpen("dd") }
                if (name == "option") closeOpen("option")
                if (name in headingTags) headingTags.forEach(::closeOpen)

                val node = HtmlNode(name, attributes = parseAttributes(trimmed, name.length))
                current().children += node
                val selfClosing = trimmed.endsWith("/")
                if (name !in voidTags && !selfClosing) stack += node
            }

            return HtmlDocument(document)
        }

        private fun findTagEnd(source: String, start: Int): Int {
            var quote: Char? = null
            var i = start
            while (i < source.length) {
                val ch = source[i]
                if (quote != null) {
                    if (ch == quote) quote = null
                } else when (ch) {
                    '"', '\'' -> quote = ch
                    '>' -> return i
                }
                i++
            }
            return -1
        }

        private fun decodeEntities(value: String): String {
            val named = mapOf(
                "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"",
                "apos" to "'", "nbsp" to "\u00A0", "copy" to "©", "reg" to "®",
                "mdash" to "—", "ndash" to "–", "hellip" to "…"
            )
            return Regex("&(#x[0-9a-fA-F]+|#\\d+|[A-Za-z][A-Za-z0-9]+);?").replace(value) { match ->
                val key = match.groupValues[1]
                when {
                    key.startsWith("#x", true) -> key.drop(2).toIntOrNull(16)?.let(::codePoint) ?: match.value
                    key.startsWith("#") -> key.drop(1).toIntOrNull()?.let(::codePoint) ?: match.value
                    else -> named[key] ?: match.value
                }
            }
        }

        private fun codePoint(value: Int): String =
            if (value in 1..0x10FFFF && value !in 0xD800..0xDFFF) String(Character.toChars(value)) else "\uFFFD"
    }

    fun visibleBlocks(): List<HtmlNode> {
        val result = mutableListOf<HtmlNode>()
        fun walk(node: HtmlNode) {
            if (node.tag in setOf("script", "style", "head", "noscript", "title")) return
            if (node.tag == "#text") {
                if (node.text.isNotBlank()) result.add(node)
                return
            }
            if (node.tag in setOf("h1", "h2", "h3", "h4", "h5", "h6", "p", "li", "a", "button", "pre")) {
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
        walk(root)
        return result
    }

    fun findFirst(tag: String): HtmlNode? {
        fun search(node: HtmlNode): HtmlNode? {
            if (node.tag.equals(tag, true)) return node
            node.children.forEach { child -> search(child)?.let { return it } }
            return null
        }
        return search(root)
    }
}
