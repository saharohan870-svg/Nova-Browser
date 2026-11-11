package com.zorvi.engine

internal enum class ZorviCssTokenType {
    IDENT, AT_KEYWORD, HASH, STRING, NUMBER, DIMENSION, PERCENTAGE, WHITESPACE, DELIMITER, EOF
}

internal data class ZorviCssToken(val type: ZorviCssTokenType, val value: String, val start: Int, val end: Int)

/** Bounded lexical scanner for the CSS subset consumed by the prototype parser. */
internal class ZorviCssTokenizer(private val source: String, private val maxLength: Int = 512_000) {
    fun tokenize(): List<ZorviCssToken> {
        val input = source.take(maxLength)
        val result = mutableListOf<ZorviCssToken>()
        var i = 0
        fun add(type: ZorviCssTokenType, start: Int, end: Int) {
            result += ZorviCssToken(type, input.substring(start, end), start, end)
        }
        while (i < input.length) {
            val start = i
            val c = input[i]
            when {
                c.isWhitespace() -> { while (i < input.length && input[i].isWhitespace()) i++; add(ZorviCssTokenType.WHITESPACE, start, i) }
                input.startsWith("/*", i) -> {
                    val close = input.indexOf("*/", i + 2)
                    i = if (close < 0) input.length else close + 2
                }
                c == '"' || c == '\'' -> {
                    val quote = c; i++
                    while (i < input.length) {
                        if (input[i] == '\\' && i + 1 < input.length) i += 2
                        else if (input[i++] == quote) break
                    }
                    add(ZorviCssTokenType.STRING, start, i)
                }
                c == '@' -> { i++; while (i < input.length && (input[i].isLetterOrDigit() || input[i] == '-' || input[i] == '_')) i++; add(ZorviCssTokenType.AT_KEYWORD, start, i) }
                c == '#' -> { i++; while (i < input.length && (input[i].isLetterOrDigit() || input[i] == '-' || input[i] == '_')) i++; add(ZorviCssTokenType.HASH, start, i) }
                c.isDigit() || (c == '.' && i + 1 < input.length && input[i + 1].isDigit()) || (c == '-' && i + 1 < input.length && input[i + 1].isDigit()) -> {
                    i++
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) i++
                    if (i < input.length && input[i] == '%') { i++; add(ZorviCssTokenType.PERCENTAGE, start, i) }
                    else {
                        val numberEnd = i
                        while (i < input.length && (input[i].isLetterOrDigit() || input[i] == '-' || input[i] == '_')) i++
                        add(if (i > numberEnd) ZorviCssTokenType.DIMENSION else ZorviCssTokenType.NUMBER, start, i)
                    }
                }
                c.isLetter() || c == '_' || c == '-' || c == '\\' -> {
                    i++
                    while (i < input.length && (input[i].isLetterOrDigit() || input[i] == '-' || input[i] == '_' || input[i] == '\\')) {
                        if (input[i] == '\\' && i + 1 < input.length) i += 2 else i++
                    }
                    add(ZorviCssTokenType.IDENT, start, i)
                }
                else -> { i++; add(ZorviCssTokenType.DELIMITER, start, i) }
            }
        }
        result += ZorviCssToken(ZorviCssTokenType.EOF, "", input.length, input.length)
        return result
    }
}
