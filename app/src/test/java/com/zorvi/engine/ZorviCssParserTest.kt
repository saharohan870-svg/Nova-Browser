package com.zorvi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZorviCssParserTest {
    @Test fun tokenizesIdentifiersDimensionsStringsAndComments() {
        val tokens = ZorviCssTokenizer("/*c*/ #hero { font-size: 12px; content: 'a;b' }").tokenize()
        assertTrue(tokens.any { it.type == ZorviCssTokenType.HASH && it.value == "#hero" })
        assertTrue(tokens.any { it.type == ZorviCssTokenType.DIMENSION && it.value == "12px" })
        assertTrue(tokens.any { it.type == ZorviCssTokenType.STRING && it.value == "'a;b'" })
        assertEquals(ZorviCssTokenType.EOF, tokens.last().type)
    }

    @Test fun parsesMultipleSelectorsAndKeepsDelimitersInsideStringsAndFunctions() {
        val rules = ZorviCssParser("""p.note, a.selected { content: "x;y:z"; background-image: url(data:image/svg+xml,a:b); color: red }""").parseStyleRules()
        assertEquals(2, rules.size)
        assertEquals("x;y:z", rules.first().declarations.first { it.property == "content" }.value.trim('"'))
        assertTrue(rules.first().declarations.any { it.property == "background-image" })
    }

    @Test fun skipsCommentsAndRecoversAfterMalformedDeclaration() {
        val rules = ZorviCssParser("/*head*/ p { color red; color: blue; /*x*/ margin: 2px } a { display: block }").parseStyleRules()
        assertEquals(2, rules.size)
        assertEquals("blue", rules.first().declarations.first { it.property == "color" }.value)
        assertEquals("a", rules.last().selector)
    }

    @Test fun ignoresAtRulesAndBoundsInput() {
        val rules = ZorviCssParser("@media screen { p { color:red } } p { color: blue }", maxLength = 80).parseStyleRules()
        assertTrue(rules.size <= 2)
    }
}
