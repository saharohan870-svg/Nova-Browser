package com.nova.engine.dom

/**
 * Conservative HTML tokenizer/tree builder for Nova Engine's initial DOM milestone.
 * It handles common markup, quoted/unquoted attributes, comments, void elements,
 * basic optional end tags, raw-text style/script elements and common entities.
 * Malformed input is recovered best-effort; this is not the full WHATWG algorithm.
 */
class HtmlDomParser {
    private val voidElements = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link",
        "meta", "param", "source", "track", "wbr"
    )
    private val rawTextElements = setOf("script", "style")
    private val namePattern = Regex("[A-Za-z][A-Za-z0-9:_-]*")

    fun parse(html: String): DomElement {
        val document = DomElement("#document")
        val stack = mutableListOf(document)
        var index = 0

        while (index < html.length) {
            if (html.startsWith("<!--", index)) {
                val end = html.indexOf("-->", index + 4)
                index = if (end < 0) html.length else end + 3
                continue
            }
            if (html[index] != '<') {
                val next = html.indexOf('<', index).let { if (it < 0) html.length else it }
                stack.last().appendText(decodeEntities(html.substring(index, next)))
                index = next
                continue
            }
            if (html.startsWith("<!", index) || html.startsWith("<?", index)) {
                val end = html.indexOf('>', index + 2)
                index = if (end < 0) html.length else end + 1
                continue
            }
            if (html.startsWith("</", index)) {
                val end = html.indexOf('>', index + 2)
                if (end < 0) break
                val rawName = html.substring(index + 2, end).trim().takeWhile { !it.isWhitespace() && it != '/' }
                if (rawName.isNotEmpty()) {
                    val matching = stack.indexOfLast { it.tagName == rawName.lowercase() }
                    if (matching > 0) while (stack.size > matching) stack.removeAt(stack.lastIndex)
                }
                index = end + 1
                continue
            }

            val parsed = parseStartTag(html, index)
            if (parsed == null) {
                stack.last().appendText("<")
                index++
                continue
            }
            val (tag, attrs, selfClosing, afterTag) = parsed
            autoCloseForStartTag(stack, tag)
            val element = DomElement(tag, attrs)
            stack.last().appendChild(element)
            index = afterTag

            if (tag in rawTextElements && !selfClosing) {
                val closePattern = Regex("</\\s*" + Regex.escape(tag) + "\\s*>", RegexOption.IGNORE_CASE)
                val close = closePattern.find(html, index)
                if (close == null) {
                    element.appendText(html.substring(index))
                    index = html.length
                } else {
                    element.appendText(html.substring(index, close.range.first))
                    index = close.range.last + 1
                }
            } else if (!selfClosing && tag !in voidElements) {
                stack += element
            }
        }
        return document
    }

    private data class StartTag(
        val tag: String,
        val attributes: Map<String, String>,
        val selfClosing: Boolean,
        val nextIndex: Int
    )

    private fun parseStartTag(source: String, start: Int): StartTag? {
        var cursor = start + 1
        while (cursor < source.length && source[cursor].isWhitespace()) cursor++
        val nameMatch = namePattern.find(source, cursor)
        if (nameMatch == null || nameMatch.range.first != cursor) return null
        val tag = nameMatch.value.lowercase()
        cursor = nameMatch.range.last + 1
        val attrs = linkedMapOf<String, String>()

        while (cursor < source.length) {
            while (cursor < source.length && source[cursor].isWhitespace()) cursor++
            if (cursor >= source.length) break
            if (source[cursor] == '>') return StartTag(tag, attrs, false, cursor + 1)
            if (source[cursor] == '/' && cursor + 1 < source.length && source[cursor + 1] == '>') {
                return StartTag(tag, attrs, true, cursor + 2)
            }
            val attrStart = cursor
            while (cursor < source.length &&
                !source[cursor].isWhitespace() && source[cursor] !in setOf('=', '>', '/')) cursor++
            if (cursor == attrStart) {
                cursor++
                continue
            }
            val attrName = source.substring(attrStart, cursor).lowercase()
            while (cursor < source.length && source[cursor].isWhitespace()) cursor++
            var value = ""
            if (cursor < source.length && source[cursor] == '=') {
                cursor++
                while (cursor < source.length && source[cursor].isWhitespace()) cursor++
                if (cursor < source.length && source[cursor] in setOf('"', '\'')) {
                    val quote = source[cursor++]
                    val valueStart = cursor
                    while (cursor < source.length && source[cursor] != quote) cursor++
                    value = source.substring(valueStart, cursor)
                    if (cursor < source.length) cursor++
                } else {
                    val valueStart = cursor
                    while (cursor < source.length &&
                        !source[cursor].isWhitespace() && source[cursor] != '>') cursor++
                    value = source.substring(valueStart, cursor).trimEnd('/')
                }
            }
            if (attrName.isNotBlank() && !attrs.containsKey(attrName)) {
                attrs[attrName] = decodeEntities(value)
            }
        }
        return StartTag(tag, attrs, false, source.length)
    }

    private fun autoCloseForStartTag(stack: MutableList<DomElement>, incoming: String) {
        val current = stack.lastOrNull()?.tagName
        val shouldClose = when (incoming) {
            "li" -> current == "li"
            "dt", "dd" -> current == "dt" || current == "dd"
            "p" -> current == "p"
            "option" -> current == "option"
            "tr" -> current == "tr"
            "td", "th" -> current == "td" || current == "th"
            "thead", "tbody", "tfoot" -> current in setOf("thead", "tbody", "tfoot")
            else -> false
        }
        if (shouldClose && stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    private fun decodeEntities(input: String): String {
        val entity = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos|nbsp);")
        return entity.replace(input) { match ->
            val key = match.groupValues[1]
            when (key) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> "\u00A0"
                else -> {
                    val codePoint = try {
                        if (key.startsWith("#x", true)) key.substring(2).toInt(16)
                        else key.substring(1).toInt()
                    } catch (_: NumberFormatException) { 0xFFFD }
                    if (!Character.isValidCodePoint(codePoint) ||
                        codePoint in 0xD800..0xDFFF || codePoint == 0) "\uFFFD"
                    else String(Character.toChars(codePoint))
                }
            }
        }
    }
}
