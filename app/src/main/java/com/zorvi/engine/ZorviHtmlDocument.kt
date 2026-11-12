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

internal data class HtmlDocument(
    val root: HtmlNode,
    val sourceTruncated: Boolean = false,
    val nestingLimitReached: Boolean = false
) {
    companion object {
        private const val MAX_SOURCE_CHARS = 2_000_000
        private const val MAX_TREE_DEPTH = 256
        private val voidTags = setOf(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr"
        )
        private val rawTextTags = setOf("script", "style", "textarea", "title", "xmp", "iframe", "noembed", "noframes", "plaintext")
        private val pClosers = setOf(
            "address", "article", "aside", "blockquote", "div", "dl", "fieldset",
            "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header",
            "hr", "main", "nav", "ol", "p", "pre", "section", "table", "ul"
        )
        private val headingTags = setOf("h1", "h2", "h3", "h4", "h5", "h6")

        fun parse(source: String): HtmlDocument {
            val input = source.take(MAX_SOURCE_CHARS)
            val document = HtmlNode("document")
            val htmlAttributes = mutableMapOf<String, String>()
            val headAttributes = mutableMapOf<String, String>()
            val bodyAttributes = mutableMapOf<String, String>()
            val html = HtmlNode("html", attributes = htmlAttributes)
            val head = HtmlNode("head", attributes = headAttributes)
            val body = HtmlNode("body", attributes = bodyAttributes)
            document.children += html
            html.children += head
            html.children += body

            val stack = mutableListOf(html, head)
            var inHead = true
            var cursor = 0
            var nestingLimitReached = false

            fun current(): HtmlNode = stack.lastOrNull() ?: body
            fun switchToBody() {
                inHead = false
                stack.clear()
                stack += html
                stack += body
            }
            fun appendText(value: String, allowInHead: Boolean = false, decode: Boolean = true) {
                if (value.isEmpty()) return
                if (inHead && value.isNotBlank() && !allowInHead) switchToBody()
                val parent = current()
                val decoded = if (decode) decodeEntities(value) else value
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
            fun closeOpenInScope(tag: String, boundaries: Set<String>) {
                for (index in stack.lastIndex downTo 1) {
                    val openTag = stack[index].tag
                    if (openTag == tag) {
                        while (stack.size > index) stack.removeAt(stack.lastIndex)
                        return
                    }
                    if (openTag in boundaries) return
                }
            }
            fun parseAttributes(sourceTag: String, from: Int): Map<String, String> {
                val result = linkedMapOf<String, String>()
                var i = from
                while (i < sourceTag.length) {
                    while (i < sourceTag.length && (sourceTag[i].isWhitespace() || sourceTag[i] == '/')) i++
                    if (i >= sourceTag.length || sourceTag[i] == '>') break
                    val start = i
                    while (i < sourceTag.length && !sourceTag[i].isWhitespace() && sourceTag[i] !in charArrayOf('=', '>')) i++
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
                            // A slash is valid inside an unquoted value (for example, href=https://host/path/).
                            while (i < sourceTag.length && !sourceTag[i].isWhitespace() && sourceTag[i] != '>') i++
                            value = sourceTag.substring(valueStart, i)
                        }
                    }
                    if (name.isNotEmpty() && name !in result) result[name] = decodeEntities(value, inAttribute = true)
                }
                return result
            }

            while (cursor < input.length) {
                val parent = current()
                if (parent.tag in rawTextTags) {
                    val closeStart = if (parent.tag == "plaintext") -1 else findRawTextEndTag(input, parent.tag, cursor)
                    val end = if (closeStart < 0) input.length else closeStart
                    appendText(
                        input.substring(cursor, end),
                        allowInHead = true,
                        decode = parent.tag in setOf("textarea", "title")
                    )
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
                    val end = findTagEnd(input, cursor + 2)
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
                    when (name) {
                        "head", "body", "html" -> switchToBody()
                        else -> if (name.isNotEmpty()) closeOpen(name)
                    }
                    continue
                }

                if (trimmed.startsWith("!") || trimmed.startsWith("?")) continue
                val name = trimmed.takeWhile { !it.isWhitespace() && it != '/' }.lowercase()
                if (name.isEmpty() || !name[0].isLetter()) {
                    appendText("<$rawTag>")
                    continue
                }
                if (name == "html" || name == "head" || name == "body") {
                    val attributes = parseAttributes(trimmed, name.length)
                    when (name) {
                        "html" -> htmlAttributes.putAll(attributes)
                        "head" -> headAttributes.putAll(attributes)
                        "body" -> {
                            bodyAttributes.putAll(attributes)
                            switchToBody()
                        }
                    }
                    continue
                }

                val headTag = name in setOf("base", "link", "meta", "title", "style", "script", "noscript")
                if (inHead && !headTag) switchToBody()
                if (inHead && name == "title") {
                    // Title is retained in the head; the body switch occurs on </head>.
                }
                closeParagraphIfNeeded(name)
                when (name) {
                    "a" -> closeOpen("a")
                    "button" -> closeOpen("button")
                    "nobr" -> closeOpen("nobr")
                    "li" -> closeOpenInScope("li", setOf("ul", "ol"))
                    "dt", "dd" -> {
                        closeOpenInScope("dt", setOf("dl"))
                        closeOpenInScope("dd", setOf("dl"))
                    }
                    "option" -> closeOpenInScope("option", setOf("select", "datalist", "optgroup"))
                    "optgroup" -> {
                        closeOpenInScope("option", setOf("select", "datalist", "optgroup"))
                        closeOpenInScope("optgroup", setOf("select", "datalist"))
                    }
                    "rp", "rt" -> {
                        closeOpenInScope("rp", setOf("ruby"))
                        closeOpenInScope("rt", setOf("ruby"))
                    }
                    "tr" -> {
                        val boundaries = setOf("table", "tbody", "thead", "tfoot")
                        closeOpenInScope("td", boundaries)
                        closeOpenInScope("th", boundaries)
                        closeOpenInScope("tr", setOf("table", "tbody", "thead", "tfoot"))
                    }
                    "td", "th" -> {
                        val boundaries = setOf("table", "tbody", "thead", "tfoot", "tr")
                        closeOpenInScope("td", boundaries)
                        closeOpenInScope("th", boundaries)
                    }
                    "thead", "tbody", "tfoot" -> {
                        val cellBoundaries = setOf("table", "tbody", "thead", "tfoot", "tr")
                        closeOpenInScope("td", cellBoundaries)
                        closeOpenInScope("th", cellBoundaries)
                        closeOpenInScope("tr", setOf("table", "tbody", "thead", "tfoot"))
                        closeOpenInScope("thead", setOf("table"))
                        closeOpenInScope("tbody", setOf("table"))
                        closeOpenInScope("tfoot", setOf("table"))
                    }
                    "caption", "colgroup" -> {
                        val cellBoundaries = setOf("table", "tbody", "thead", "tfoot", "tr")
                        closeOpenInScope("td", cellBoundaries)
                        closeOpenInScope("th", cellBoundaries)
                        closeOpenInScope("tr", setOf("table", "tbody", "thead", "tfoot"))
                        closeOpenInScope("thead", setOf("table"))
                        closeOpenInScope("tbody", setOf("table"))
                        closeOpenInScope("tfoot", setOf("table"))
                        closeOpenInScope(name, setOf("table"))
                    }
                }
                if (name in headingTags) headingTags.forEach(::closeOpen)

                // Close caption/column-group context before switching to table rows.
                if (name in setOf("tbody", "thead", "tfoot", "tr", "td", "th")) {
                    closeOpenInScope("caption", setOf("table"))
                    closeOpenInScope("colgroup", setOf("table"))
                }
                // The tree builder inserts missing table section and row containers.
                if (name == "tr" && current().tag == "table") {
                    val impliedBody = HtmlNode("tbody")
                    current().children += impliedBody
                    stack += impliedBody
                } else if ((name == "td" || name == "th") && current().tag == "table") {
                    val impliedBody = HtmlNode("tbody")
                    current().children += impliedBody
                    stack += impliedBody
                    val impliedRow = HtmlNode("tr")
                    current().children += impliedRow
                    stack += impliedRow
                } else if ((name == "td" || name == "th") && current().tag in setOf("tbody", "thead", "tfoot")) {
                    val impliedRow = HtmlNode("tr")
                    current().children += impliedRow
                    stack += impliedRow
                }

                val node = HtmlNode(name, attributes = parseAttributes(trimmed, name.length))
                current().children += node
                // HTML ignores one initial LF (or CRLF) in preformatted text elements.
                if (name in setOf("pre", "listing", "textarea") && cursor < input.length) {
                    if (input[cursor] == '\r' && cursor + 1 < input.length && input[cursor + 1] == '\n') {
                        cursor += 2
                    } else if (input[cursor] == '\n' || input[cursor] == '\r') {
                        cursor++
                    }
                }
                // In HTML namespace, a trailing slash does not self-close non-void elements.
                if (name !in voidTags) {
                    if (stack.size < MAX_TREE_DEPTH) stack += node else nestingLimitReached = true
                }
            }

            return HtmlDocument(document, sourceTruncated = source.length > input.length, nestingLimitReached = nestingLimitReached)
        }

        private fun findRawTextEndTag(source: String, tag: String, start: Int): Int {
            var candidate = source.indexOf("</$tag", start, ignoreCase = true)
            while (candidate >= 0) {
                val afterName = candidate + tag.length + 2
                if (afterName >= source.length || source[afterName].isWhitespace() ||
                    source[afterName] == '>' || source[afterName] == '/') return candidate
                candidate = source.indexOf("</$tag", afterName, ignoreCase = true)
            }
            return -1
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

        private fun decodeEntities(value: String, inAttribute: Boolean = false): String {
            val named = mapOf(
                "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
                "nbsp" to "\u00A0", "copy" to "©", "reg" to "®", "trade" to "™",
                "mdash" to "—", "ndash" to "–", "hellip" to "…", "laquo" to "«",
                "raquo" to "»", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“",
                "rdquo" to "”", "bull" to "•", "middot" to "·", "times" to "×",
                "divide" to "÷", "plusmn" to "±", "euro" to "€", "pound" to "£",
                "yen" to "¥", "cent" to "¢", "deg" to "°", "para" to "¶",
                "sect" to "§", "micro" to "µ", "acute" to "´", "aacute" to "á",
                "eacute" to "é", "iacute" to "í", "oacute" to "ó", "uacute" to "ú",
                "Aacute" to "Á", "Eacute" to "É", "uuml" to "ü", "Uuml" to "Ü",
                "ntilde" to "ñ", "Ntilde" to "Ñ", "ccedil" to "ç", "Ccedil" to "Ç"
            )
            val legacyNoSemicolon = setOf("amp", "lt", "gt", "quot", "nbsp", "copy", "reg")
            return Regex("&(#x[0-9a-fA-F]+;?|#\\d+;?|[A-Za-z][A-Za-z0-9]*;?)").replace(value) { match ->
                val raw = match.groupValues[1]
                val key = raw.removeSuffix(";")
                when {
                    key.startsWith("#x", true) -> key.drop(2).toIntOrNull(16)?.let(::codePoint) ?: "\uFFFD"
                    key.startsWith("#") -> key.drop(1).toIntOrNull()?.let(::codePoint) ?: "\uFFFD"
                    named.containsKey(key) && (raw.endsWith(";") || (
                        key in legacyNoSemicolon &&
                            (!inAttribute || match.range.last + 1 >= value.length ||
                                (!value[match.range.last + 1].isLetterOrDigit() && value[match.range.last + 1] != '='))
                    )) -> named.getValue(key)
                    else -> match.value
                }
            }
        }

        private fun codePoint(value: Int): String {
            val windows1252 = mapOf(
                0x80 to 0x20AC, 0x82 to 0x201A, 0x83 to 0x0192, 0x84 to 0x201E,
                0x85 to 0x2026, 0x86 to 0x2020, 0x87 to 0x2021, 0x88 to 0x02C6,
                0x89 to 0x2030, 0x8A to 0x0160, 0x8B to 0x2039, 0x8C to 0x0152,
                0x8E to 0x017D, 0x91 to 0x2018, 0x92 to 0x2019, 0x93 to 0x201C,
                0x94 to 0x201D, 0x95 to 0x2022, 0x96 to 0x2013, 0x97 to 0x2014,
                0x98 to 0x02DC, 0x99 to 0x2122, 0x9A to 0x0161, 0x9B to 0x203A,
                0x9C to 0x0153, 0x9E to 0x017E, 0x9F to 0x0178
            )
            val normalized = windows1252[value] ?: value
            return if (normalized in 1..0x10FFFF && normalized !in 0xD800..0xDFFF)
                String(Character.toChars(normalized)) else "\uFFFD"
        }
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

    fun findAll(tag: String): List<HtmlNode> = buildList {
        fun collect(node: HtmlNode) {
            if (node.tag.equals(tag, true)) add(node)
            node.children.forEach(::collect)
        }
        collect(root)
    }

    fun textContent(node: HtmlNode): String = buildString {
        fun collect(current: HtmlNode) {
            if (current.tag == "#text") append(current.text)
            else current.children.forEach(::collect)
        }
        collect(node)
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
