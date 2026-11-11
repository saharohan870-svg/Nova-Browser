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
        assertEquals("Example", document.textContent(html.children[0].children.single()))
        assertEquals("body", html.children[1].tag)
        assertEquals("Hello", document.textContent(html.children[1].children.single()))
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
    @Test fun preservesAttributesOnExplicitHtmlHeadAndBodyElements() {
        val document = HtmlDocument.parse("""<html lang="en"><head data-head=yes><title>Page</title></head><body class="site" id=main><p>Content</p></body></html>""")
        val html = document.findFirst("html")!!
        val head = document.findFirst("head")!!
        val body = document.findFirst("body")!!
        assertEquals("en", html.attributes["lang"])
        assertEquals("yes", head.attributes["data-head"])
        assertEquals("site", body.attributes["class"])
        assertEquals("main", body.attributes["id"])
    }

    @Test fun keepsScriptAndStyleRawTextInBodyInTheCorrectTreeNodes() {
        val document = HtmlDocument.parse("<body><script>const x = '<p>not markup</p>';</script><style>.a > b { color: red }</style><p>Real text</p></body>")
        assertEquals("const x = '<p>not markup</p>';", document.findFirst("script")!!.children.single().text)
        assertEquals(".a > b { color: red }", document.findFirst("style")!!.children.single().text)
        assertTrue(document.visibleBlocks().any { it.text == "Real text" })
    }

    @Test fun scriptRawTextDoesNotDecodeEntitiesOrCloseOnLongerTagNames() {
        val document = HtmlDocument.parse("<script>const x = '&amp;';</scripture>still script text</script><p>After</p>")
        assertEquals("const x = '&amp;';</scripture>still script text", document.findFirst("script")!!.children.single().text)
        assertTrue(document.visibleBlocks().any { it.text == "After" })
    }

    @Test fun rcdataTitleDecodesEntities() {
        val document = HtmlDocument.parse("<title>Tom &amp; Jerry</title><p>Body</p>")
        assertEquals("Tom & Jerry", document.textContent(document.findFirst("title")!!))
    }

    @Test fun malformedInputsDoNotCrashParser() {
        val samples = listOf(
            "<", "<<<<", "<a href='unterminated", "<div><span><p>x</div>",
            "<!-- never closed", "<script>unterminated <b>text", "<p a==b c=d>value",
            "<!DOCTYPE html><html><body><p>ok"
        )
        samples.forEach { source ->
            val parsed = HtmlDocument.parse(source)
            assertNotNull(parsed.root)
        }
    }

}
