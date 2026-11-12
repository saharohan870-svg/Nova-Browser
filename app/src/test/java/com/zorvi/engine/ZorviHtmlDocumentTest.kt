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

    @Test fun keepsContentAfterExplicitBodyAndHtmlEndTagsInBody() {
        val document = HtmlDocument.parse("<html><body><p>Before</p></body></html><p>After</p>")
        val body = document.findFirst("body")!!
        assertEquals(2, body.children.count { it.tag == "p" })
        assertEquals("After", document.textContent(body.children.last()))
    }

    @Test fun ignoresSelfClosingSlashOnNonVoidHtmlElements() {
        val document = HtmlDocument.parse("<div/>inside<span/>nested</span>end</div><p>After</p>")
        val div = document.findFirst("div")!!
        assertEquals("inside", document.textContent(div.children.first { it.tag == "#text" }))
        assertEquals("nested", document.textContent(div.children.first { it.tag == "span" }))
        assertEquals("After", document.textContent(document.findFirst("p")!!))
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
        assertTrue(document.sourceTruncated)
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


    @Test fun preservesSlashesInUnquotedAttributeValues() {
        val document = HtmlDocument.parse("<a href=https://example.com/path/>Open</a><img src=/assets/icon.png>")
        assertEquals("https://example.com/path/", document.findFirst("a")!!.attributes["href"])
        assertEquals("/assets/icon.png", document.findFirst("img")!!.attributes["src"])
    }

    @Test fun closesTableCellsAndRowsWhenEndTagsAreOmitted() {
        val document = HtmlDocument.parse("<table><tr><td>A<td>B<tr><td>C</table>")
        val table = document.findFirst("table")!!
        val rows = table.children.filter { it.tag == "tr" }
        assertEquals(2, rows.size)
        assertEquals(2, rows[0].children.count { it.tag == "td" })
        assertEquals(1, rows[1].children.count { it.tag == "td" })
    }


    @Test fun preservesOuterListItemWhenNestedListStarts() {
        val document = HtmlDocument.parse("<ul><li>Outer<ul><li>Inner</li></ul></li><li>Second</li></ul>")
        val list = document.findFirst("ul")!!
        assertEquals(2, list.children.count { it.tag == "li" })
        val outer = list.children.first { it.tag == "li" }
        assertEquals(1, outer.children.count { it.tag == "ul" })
        assertEquals("Inner", document.textContent(outer.children.single { it.tag == "ul" }.children.single { it.tag == "li" }))
    }

    @Test fun preservesOuterTableCellWhenNestedTableStarts() {
        val document = HtmlDocument.parse("<table><tr><td>Outer<table><tr><td>Inner</table>Tail</td></tr></table>")
        val outerCell = document.findFirst("td")!!
        assertTrue(document.textContent(outerCell).contains("Outer"))
        assertTrue(document.textContent(outerCell).contains("Tail"))
        assertEquals("Inner", document.textContent(document.findAll("td")[1]))
    }

    @Test fun doesNotDecodeAmbiguousLegacyReferencesInsideAttributes() {
        val document = HtmlDocument.parse("<a title=\"&copy=1 &copycat &copy;\">Text &copy</a>")
        assertEquals("&copy=1 &copycat ©", document.findFirst("a")!!.attributes["title"])
        assertEquals("Text ©", document.textContent(document.findFirst("a")!!))
    }

    @Test fun decodesCommonNamedAndWindows1252NumericReferences() {
        val document = HtmlDocument.parse("<p>&euro; &mdash; &hellip; &#x80; &#128;</p>")
        assertEquals("€ — … € €", document.textContent(document.findFirst("p")!!))
    }

    @Test fun handlesDoctypeWithQuotedGreaterThanCharacter() {
        val document = HtmlDocument.parse("<!DOCTYPE html PUBLIC \"x>y\"><p>Content</p>")
        assertEquals("Content", document.textContent(document.findFirst("p")!!))
    }

    @Test fun boundsNestingDepthOnAdversarialMarkup() {
        val source = "<div>".repeat(2_000) + "safe" + "</div>".repeat(2_000)
        val document = HtmlDocument.parse(source)
        assertTrue(document.textContent(document.findFirst("body")!!).contains("safe"))
        assertTrue(document.nestingLimitReached)
    }


    @Test fun stripsOneInitialNewlineFromPreformattedElements() {
        val document = HtmlDocument.parse("<pre>\nFirst</pre><textarea>\r\nSecond</textarea><listing>\rThird</listing>")
        assertEquals("First", document.textContent(document.findFirst("pre")!!))
        assertEquals("Second", document.textContent(document.findFirst("textarea")!!))
        assertEquals("Third", document.textContent(document.findFirst("listing")!!))
    }

    @Test fun treatsPlaintextAsTextUntilEndOfInput() {
        val document = HtmlDocument.parse("<plaintext><b>not markup</b> &amp; still text")
        assertEquals("<b>not markup</b> &amp; still text", document.textContent(document.findFirst("plaintext")!!))
    }

    @Test fun handlesLegacyRawTextElementsWithoutParsingNestedMarkup() {
        val document = HtmlDocument.parse("<xmp><b>literal</b></xmp><iframe><p>frame text</p></iframe><p>After</p>")
        assertEquals("<b>literal</b>", document.textContent(document.findFirst("xmp")!!))
        assertEquals("<p>frame text</p>", document.textContent(document.findFirst("iframe")!!))
        assertTrue(document.visibleBlocks().any { it.text == "After" })
    }

    @Test fun reportsWhenParserNestingLimitIsReached() {
        val source = "<div>".repeat(300) + "text"
        val document = HtmlDocument.parse(source)
        assertTrue(document.nestingLimitReached)
        assertTrue(document.textContent(document.findFirst("body")!!).contains("text"))
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
