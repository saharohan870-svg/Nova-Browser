package com.zorvi.engine

import android.graphics.Color

/** Small, intentionally incomplete CSS cascade for Zorvi Engine's prototype. */
internal data class ZorviCssStyle(
    var display: String = "block",
    var color: Int = Color.rgb(30, 30, 30),
    var backgroundColor: Int = Color.TRANSPARENT,
    var fontSizeSp: Float = 17f,
    var bold: Boolean = false,
    var textAlign: String = "left",
    var marginTop: Float = 8f,
    var marginBottom: Float = 8f,
    var padding: Float = 0f
)

internal class ZorviCssStyleSheet(cssText: String) {
    private data class Rule(val selector: String, val declarations: Map<String, String>, val specificity: Int, val order: Int)
    private val rules = ZorviCssParser(cssText).parseStyleRules().map { parsed ->
        Rule(parsed.selector, parsed.declarations.associate { it.property to it.value }, specificity(parsed.selector), parsed.sourceOrder)
    }

    fun styleFor(node: HtmlNode): ZorviCssStyle {
        val style = ZorviCssStyle()
        when (node.tag) {
            "h1" -> { style.fontSizeSp = 28f; style.bold = true; style.marginTop = 18f; style.marginBottom = 12f }
            "h2" -> { style.fontSizeSp = 23f; style.bold = true; style.marginTop = 16f }
            "h3" -> { style.fontSizeSp = 20f; style.bold = true }
            "pre" -> style.fontSizeSp = 14f
            "a" -> style.color = Color.rgb(20, 90, 190)
            "li" -> { style.marginTop = 3f; style.marginBottom = 3f }
            "title" -> style.display = "none"
        }
        rules.filter { matches(it.selector, node) }
            .sortedWith(compareBy<Rule> { it.specificity }.thenBy { it.order })
            .forEach { apply(style, it.declarations) }
        val inline = ZorviCssParser("x { ${node.attributes["style"].orEmpty()} }").parseStyleRules()
            .firstOrNull()?.declarations?.associate { it.property to it.value }.orEmpty()
        apply(style, inline)
        return style
    }

    private fun apply(style: ZorviCssStyle, declarations: Map<String, String>) {
        declarations.forEach { (property, raw) ->
            val value = raw.trim().lowercase()
            when (property) {
                "display" -> if (value in setOf("none", "block", "inline", "inline-block")) style.display = value
                "color" -> parseColor(value)?.let { style.color = it }
                "background", "background-color" -> parseColor(value.substringBefore(" ").trim())?.let { style.backgroundColor = it }
                "font-size" -> parseLength(value)?.let { style.fontSizeSp = it.coerceIn(8f, 64f) }
                "font-weight" -> style.bold = value == "bold" || (value.toIntOrNull() ?: 400) >= 600
                "text-align" -> if (value in setOf("left", "center", "right")) style.textAlign = value
                "margin" -> parseLength(value.substringBefore(" "))?.let { style.marginTop = it; style.marginBottom = it }
                "margin-top" -> parseLength(value)?.let { style.marginTop = it }
                "margin-bottom" -> parseLength(value)?.let { style.marginBottom = it }
                "padding" -> parseLength(value.substringBefore(" "))?.let { style.padding = it.coerceIn(0f, 64f) }
            }
        }
    }

    private fun matches(selectorGroup: String, node: HtmlNode): Boolean =
        selectorGroup.split(',').any { selector ->
            val simple = selector.trim().substringAfterLast(' ').substringAfterLast('>').trim()
            if (simple.isEmpty()) false else {
                val tag = Regex("^[a-zA-Z][a-zA-Z0-9-]*").find(simple)?.value?.lowercase()
                val id = Regex("#([\\w-]+)").find(simple)?.groupValues?.get(1)
                val classes = Regex("\\.([\\w-]+)").findAll(simple).map { it.groupValues[1] }.toList()
                (tag == null || tag == node.tag) && (id == null || id == node.attributes["id"]) &&
                    classes.all { it in node.attributes["class"].orEmpty().split(Regex("\\s+")) }
            }
        }

    private fun specificity(selector: String): Int =
        Regex("#[\\w-]+").findAll(selector).count() * 100 +
            Regex("\\.[\\w-]+").findAll(selector).count() * 10 +
            Regex("(?:^|[ >])(?:[a-zA-Z][\\w-]*)").findAll(selector).count()

    private fun parseLength(value: String): Float? {
        val number = Regex("^-?\\d+(?:\\.\\d+)?").find(value)?.value?.toFloatOrNull() ?: return null
        return when {
            value.endsWith("px") || value.matches(Regex("^-?\\d+(?:\\.\\d+)?$")) -> number
            value.endsWith("pt") -> number * 1.333f
            value.endsWith("em") || value.endsWith("rem") -> number * 17f
            else -> null
        }
    }

    private fun parseColor(value: String): Int? = when (value) {
        "transparent" -> Color.TRANSPARENT
        "black" -> Color.BLACK
        "white" -> Color.WHITE
        "red" -> Color.RED
        "blue" -> Color.BLUE
        "green" -> Color.GREEN
        "gray", "grey" -> Color.GRAY
        else -> try {
            when {
                value.matches(Regex("#[0-9a-f]{3}")) -> Color.rgb(value[1].digitToInt(16) * 17, value[2].digitToInt(16) * 17, value[3].digitToInt(16) * 17)
                value.matches(Regex("#[0-9a-f]{6}")) || value.matches(Regex("#[0-9a-f]{8}")) -> Color.parseColor(value)
                else -> null
            }
        } catch (_: IllegalArgumentException) { null }
    }
}
