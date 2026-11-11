package com.zorvi.engine

internal data class ZorviCssDeclaration(val property: String, val value: String)
internal data class ZorviCssRule(val selector: String, val declarations: List<ZorviCssDeclaration>, val sourceOrder: Int)

/** Recovering parser for ordinary style rules; at-rules and nested rules are skipped. */
internal class ZorviCssParser(private val source: String, private val maxLength: Int = 512_000) {
    fun parseStyleRules(): List<ZorviCssRule> {
        val input = source.take(maxLength)
        val output = mutableListOf<ZorviCssRule>()
        var cursor = 0
        var order = 0
        while (cursor < input.length) {
            cursor = skipSpaceAndComments(input, cursor)
            if (cursor >= input.length) break
            val open = findTopLevel(input, cursor, '{') ?: break
            val prelude = input.substring(cursor, open).trim()
            val close = findMatchingBrace(input, open + 1) ?: break
            val body = input.substring(open + 1, close)
            if (prelude.isNotEmpty() && !prelude.startsWith("@")) {
                val declarations = parseDeclarations(body)
                if (declarations.isNotEmpty()) {
                    prelude.split(',').map(String::trim).filter(String::isNotEmpty).forEach { selector ->
                        output += ZorviCssRule(selector, declarations, order++)
                    }
                }
            }
            cursor = close + 1
        }
        return output
    }

    private fun parseDeclarations(body: String): List<ZorviCssDeclaration> {
        val result = mutableListOf<ZorviCssDeclaration>()
        splitTopLevel(body, ';').forEach { part ->
            val colon = findTopLevel(part, 0, ':') ?: return@forEach
            val property = part.substring(0, colon).trim().lowercase()
            val value = part.substring(colon + 1).trim()
            if (property.matches(Regex("^-?[a-zA-Z_][a-zA-Z0-9_-]*$")) && value.isNotEmpty()) {
                result += ZorviCssDeclaration(property, value)
            }
        }
        return result
    }

    private fun splitTopLevel(value: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        var start = 0; var i = 0; var depth = 0; var quote: Char? = null
        while (i < value.length) {
            val c = value[i]
            if (quote != null) {
                if (c == '\\') i++ else if (c == quote) quote = null
            } else when {
                value.startsWith("/*", i) -> { val end = value.indexOf("*/", i + 2); i = if (end < 0) value.length else end + 1 }
                c == '"' || c == '\'' -> quote = c
                c == '(' || c == '[' -> depth++
                c == ')' || c == ']' -> if (depth > 0) depth--
                c == delimiter && depth == 0 -> { result += value.substring(start, i); start = i + 1 }
            }
            i++
        }
        result += value.substring(start)
        return result
    }

    private fun findTopLevel(value: String, start: Int, wanted: Char): Int? {
        var i = start; var depth = 0; var quote: Char? = null
        while (i < value.length) {
            val c = value[i]
            if (quote != null) {
                if (c == '\\') i++ else if (c == quote) quote = null
            } else when {
                value.startsWith("/*", i) -> { val end = value.indexOf("*/", i + 2); i = if (end < 0) value.length else end + 1 }
                c == '"' || c == '\'' -> quote = c
                c == '(' || c == '[' -> depth++
                c == ')' || c == ']' -> if (depth > 0) depth--
                c == wanted && depth == 0 -> return i
                c == '}' && wanted == '{' && depth == 0 -> return null
            }
            i++
        }
        return null
    }

    private fun findMatchingBrace(value: String, start: Int): Int? {
        var i = start; var depth = 1; var quote: Char? = null
        while (i < value.length) {
            val c = value[i]
            if (quote != null) {
                if (c == '\\') i++ else if (c == quote) quote = null
            } else when {
                value.startsWith("/*", i) -> { val end = value.indexOf("*/", i + 2); i = if (end < 0) value.length else end + 1 }
                c == '"' || c == '\'' -> quote = c
                c == '{' -> depth++
                c == '}' -> { depth--; if (depth == 0) return i }
            }
            i++
        }
        return null
    }

    private fun skipSpaceAndComments(value: String, start: Int): Int {
        var i = start
        while (i < value.length) {
            if (value[i].isWhitespace()) i++
            else if (value.startsWith("/*", i)) {
                val end = value.indexOf("*/", i + 2)
                i = if (end < 0) value.length else end + 2
            } else break
        }
        return i
    }
}
