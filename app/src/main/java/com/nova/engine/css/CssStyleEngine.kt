package com.nova.engine.css

/**
 * Small, dependency-free CSS cascade for Nova Engine.
 *
 * Supported selectors: type, universal, #id, .class, compound selectors,
 * and descendant combinators. Unsupported selectors are ignored safely.
 * This is an intentionally limited first CSS milestone, not a full CSS spec.
 */
interface CssElement {
    val tagName: String
    val id: String get() = ""
    val classes: Set<String> get() = emptySet()
    val parent: CssElement? get() = null
}

data class ComputedStyle internal constructor(
    private val values: Map<String, String>
) {
    operator fun get(property: String): String? = values[property.lowercase()]
    fun asMap(): Map<String, String> = values.toMap()

    companion object {
        val EMPTY = ComputedStyle(emptyMap())
    }
}

class CssStyleEngine {
    private data class Specificity(val ids: Int, val classes: Int, val types: Int) :
        Comparable<Specificity> {
        override fun compareTo(other: Specificity): Int =
            compareValuesBy(this, other, Specificity::ids, Specificity::classes, Specificity::types)
    }

    private data class Compound(
        val tag: String?,
        val id: String?,
        val classes: Set<String>
    )

    private data class Selector(
        val parts: List<Compound>,
        val specificity: Specificity
    )

    private data class Declaration(val value: String, val important: Boolean)
    private data class Rule(
        val selector: Selector,
        val declarations: Map<String, Declaration>,
        val order: Int
    )

    private val rules = mutableListOf<Rule>()
    private var nextOrder = 0

    /** Adds CSS to the current stylesheet. Existing rules remain in place. */
    fun addStylesheet(css: String) {
        val withoutComments = css.replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        val blockPattern = Regex("([^{}]+)\\{([^{}]*)}")
        for (match in blockPattern.findAll(withoutComments)) {
            val declarations = parseDeclarations(match.groupValues[2])
            if (declarations.isEmpty()) continue

            // A comma-separated selector list creates one rule per valid selector.
            match.groupValues[1].split(',').forEach { rawSelector ->
                parseSelector(rawSelector.trim())?.let { selector ->
                    rules += Rule(selector, declarations, nextOrder++)
                }
            }
        }
    }

    /** Removes all loaded stylesheets. */
    fun clear() {
        rules.clear()
        nextOrder = 0
    }

    /**
     * Computes the winning declarations for an element.
     * Pass a parent style explicitly when the caller already has one cached.
     */
    fun computeStyle(
        element: CssElement,
        parentStyle: ComputedStyle? = element.parent?.let { computeStyle(it) }
    ): ComputedStyle {
        val winners = mutableMapOf<String, Triple<Declaration, Specificity, Int>>()

        for (rule in rules) {
            if (!matches(rule.selector, element)) continue
            for ((property, declaration) in rule.declarations) {
                val previous = winners[property]
                val candidate = Triple(declaration, rule.selector.specificity, rule.order)
                if (previous == null || outranks(candidate, previous)) {
                    winners[property] = candidate
                }
            }
        }

        val result = mutableMapOf<String, String>()
        // CSS inherited properties are copied first; local declarations override them.
        if (parentStyle != null) {
            for (property in inheritedProperties) {
                parentStyle[property]?.let { result[property] = it }
            }
        }
        winners.forEach { (property, winner) -> result[property] = winner.first.value }
        return ComputedStyle(result)
    }

    private fun outranks(
        candidate: Triple<Declaration, Specificity, Int>,
        previous: Triple<Declaration, Specificity, Int>
    ): Boolean {
        val a = candidate.first
        val b = previous.first
        if (a.important != b.important) return a.important
        val specificityResult = candidate.second.compareTo(previous.second)
        return specificityResult > 0 ||
            (specificityResult == 0 && candidate.third >= previous.third)
    }

    private fun parseDeclarations(source: String): Map<String, Declaration> {
        val output = linkedMapOf<String, Declaration>()
        for (part in source.split(';')) {
            val colon = part.indexOf(':')
            if (colon <= 0) continue
            val property = part.substring(0, colon).trim().lowercase()
            var value = part.substring(colon + 1).trim()
            if (property.isEmpty() || value.isEmpty()) continue
            val important = Regex("\\s*!important\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(value)
            if (important) value = value.replace(
                Regex("\\s*!important\\s*$", RegexOption.IGNORE_CASE), ""
            ).trim()
            if (value.isNotEmpty()) output[property] = Declaration(value, important)
        }
        return output
    }

    private fun parseSelector(source: String): Selector? {
        if (source.isBlank() || source.any { it in ":[]>+~" }) return null
        val tokens = source.trim().split(Regex("\\s+"))
        if (tokens.isEmpty()) return null
        val parts = mutableListOf<Compound>()
        var idCount = 0
        var classCount = 0
        var typeCount = 0

        for (token in tokens) {
            val match = Regex("^([a-zA-Z][a-zA-Z0-9_-]*|\\*)?((?:[.#][a-zA-Z_][a-zA-Z0-9_-]*)*)$")
                .matchEntire(token) ?: return null
            val tag = match.groupValues[1].takeIf { it.isNotEmpty() && it != "*" }?.lowercase()
            val suffix = match.groupValues[2]
            val idMatch = Regex("#([a-zA-Z_][a-zA-Z0-9_-]*)").findAll(suffix).map { it.groupValues[1] }.toList()
            val classMatches = Regex("\\.([a-zA-Z_][a-zA-Z0-9_-]*)").findAll(suffix).map { it.groupValues[1] }.toSet()
            if (idMatch.size > 1) return null
            if (tag == null && idMatch.isEmpty() && classMatches.isEmpty()) return null
            idCount += idMatch.size
            classCount += classMatches.size
            if (tag != null) typeCount++
            parts += Compound(tag, idMatch.firstOrNull(), classMatches)
        }
        return Selector(parts, Specificity(idCount, classCount, typeCount))
    }

    private fun matches(selector: Selector, element: CssElement): Boolean {
        var current: CssElement? = element
        var index = selector.parts.lastIndex
        if (current == null || !matchesCompound(selector.parts[index], current)) return false
        index--

        // Each remaining selector component must match an ancestor, not necessarily the parent.
        while (index >= 0) {
            current = current?.parent
            while (current != null && !matchesCompound(selector.parts[index], current)) {
                current = current.parent
            }
            if (current == null) return false
            index--
        }
        return true
    }

    private fun matchesCompound(compound: Compound, element: CssElement): Boolean {
        if (compound.tag != null && compound.tag != element.tagName.lowercase()) return false
        if (compound.id != null && compound.id != element.id) return false
        return element.classes.containsAll(compound.classes)
    }

    private companion object {
        val inheritedProperties = setOf(
            "color", "font-family", "font-size", "font-style", "font-weight",
            "line-height", "text-align", "text-decoration", "text-transform",
            "visibility", "white-space", "word-spacing", "letter-spacing"
        )
    }
}
