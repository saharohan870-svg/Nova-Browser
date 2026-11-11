package com.zorvi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZorviResourceDiscoveryTest {
    @Test fun discoversCommonSubresourcesAndResolvesBaseUrl() {
        val html = """
            <html><head>
              <base href="/assets/">
              <link rel="stylesheet" href="site.css">
              <script src="app.js"></script>
            </head><body>
              <img src="logo.png">
              <video poster="preview.jpg"><source src="clip.mp4"></video>
            </body></html>
        """.trimIndent()
        val resources = ZorviResourceDiscovery.discover(
            HtmlDocument.parse(html),
            ZorviUrl("https://example.test/pages/index.html")
        )
        assertEquals(5, resources.size)
        assertEquals("https://example.test/assets/site.css", resources[0].url.href)
        assertEquals(ZorviResourceType.STYLESHEET, resources[0].type)
        assertTrue(resources.any { it.url.href.endsWith("/app.js") && it.type == ZorviResourceType.SCRIPT })
        assertTrue(resources.any { it.url.href.endsWith("/logo.png") && it.type == ZorviResourceType.IMAGE })
        assertTrue(resources.any { it.url.href.endsWith("/clip.mp4") && it.type == ZorviResourceType.MEDIA })
    }

    @Test fun ignoresUnsupportedSchemesAndMalformedResourceUrls() {
        val document = HtmlDocument.parse(
            """<html><body><img src="javascript:alert(1)"><script src="http://[bad"></script></body></html>"""
        )
        val resources = ZorviResourceDiscovery.discover(document, ZorviUrl("https://example.test/"))
        assertTrue(resources.isEmpty())
    }
}
