package com.zorvi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZorviHtmlDocumentTest {
    @Test fun createsImpliedDocumentHeadAndBody() {
        val document = HtmlDocument.parse("<title>Example</title><p>Hello</p>")
        val html = document.root.children.single()
        assertEquals("html", html.tag)
        assertEquals("head", html.children[0].tag)
        assertEquals("Example", html.children[0].children.single().text)
        assertEquals("body", html.children[1].tag)
        assertEquals("Hello", html.children[1].children.single().children.single().text)
    }

    @Test fun parsesQuotedAndUnquotedAttributesAndDecodesEntities() {
        val document = HtmlDocument.parse("""<p id="welcome" class=hero title='A &amp; B'>Tom &amp; Jerry &#x1F600;</p>""")
        val paragraph = document.findFirst("p")
        assertNotNull(paragraph)
        assertEquals("welcome", paragraph!!.attributes["id"])
        assertEquals("hero", paragraph.attributes["class"])
        assertEquals("A & B", paragraph.attributes["title"])
        assertEquals("Tom & Jerry 😀", paragraph.children.single().text)
    }

    @Test fun handlesCommentsVoidElementsAndMalformedClosingTags() {
        val document = HtmlDocument.parse("<p>One<br>Two<!-- hidden --><img src=x></p></oops>")
        val paragraph = document.findFirst("p")
        assertNotNull(paragraph)
        assertTrue(paragraph!!.children.any { it.tag == "br" })
        assertTrue(paragraph.children.any { it.tag == "img" })
        assertTrue(paragraph.children.none { it.text.contains("hidden") })
    }

    @Test fun rawTextElementsRetainTheirContentsButStayOutOfVisibleBlocks() {
        val document = HtmlDocument.parse("<script>if (a < b) run();</script><style>.x { color:red }</style><p>Visible</p>")
        assertEquals("if (a < b) run();", document.findFirst("script")!!.children.single().text)
        assertEquals(".x { color:red }", document.findFirst("style")!!.children.single().text)
        assertTrue(document.visibleBlocks().none { it.tag == "script" || it.tag == "style" })
        assertTrue(document.visibleBlocks().any { it.text == "Visible" })
    }

    @Test fun implicitlyClosesParagraphAndListItems() {
        val document = HtmlDocument.parse("<p>First<p>Second<ul><li>A<li>B</ul>")
        val body = document.findFirst("body")!!
        assertEquals(2, body.children.count { it.tag == "p" })
        val list = document.findFirst("ul")!!
        assertEquals(2, list.children.count { it.tag == "li" })
    }

    @Test fun ignoresOversizedTailWithoutCrashing() {
        val document = HtmlDocument.parse("<p>start</p>" + "x".repeat(2_000_100))
        assertTrue(document.visibleBlocks().any { it.text == "start" })
    }
}
