package com.zorvi.engine

import org.junit.Assert.*
import org.junit.Test

class ZorviUrlTest {
    @Test fun parsesHttpUrlAndComputesOrigin() {
        val url = ZorviUrl("https://Example.com:443/a/b?q=hello#top")
        assertEquals("https:", url.protocol)
        assertEquals("example.com", url.hostname)
        assertEquals(443, url.port)
        assertEquals("/a/b", url.pathname)
        assertEquals("?q=hello", url.search)
        assertEquals("#top", url.hash)
        assertEquals("https://example.com", url.origin)
    }

    @Test fun resolvesRelativeUrlAgainstBase() {
        val base = ZorviUrl("https://example.com/docs/index.html")
        assertEquals("https://example.com/images/a.png", ZorviUrl("../images/a.png", base).href)
        assertEquals("https://example.com/docs/index.html?mode=1", ZorviUrl("?mode=1", base).href)
    }

    @Test fun rejectsUnsafeAndMalformedSchemes() {
        listOf("javascript:alert(1)", "file:///etc/passwd", "data:text/plain,hi", "not a url")
            .forEach { value ->
                try { ZorviUrl(value); fail("Expected rejection for $value") }
                catch (_: IllegalArgumentException) { }
            }
    }

    @Test fun rejectsCredentialBearingUrls() {
        try { ZorviUrl("https://user:pass@example.com/"); fail("Expected credentials to be rejected") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun searchParamsPreserveDuplicatesAndDecodeFormValues() {
        val params = ZorviUrlSearchParams("?tag=one&tag=two&empty&name=hello+world")
        assertEquals("one", params.get("tag"))
        assertEquals(listOf("one", "two"), params.getAll("tag"))
        assertEquals("", params.get("empty"))
        assertEquals("hello world", params.get("name"))
    }

    @Test fun searchParamsAppendSetAndDelete() {
        val params = ZorviUrlSearchParams("a=1&a=2&b=3")
        params.append("a", "4")
        params.set("b", "changed")
        params.delete("a")
        assertEquals("b=changed", params.toQueryString())
        assertFalse(params.has("a"))
    }

    @Test fun searchParamsEncodeSpacesAndReservedCharacters() {
        val params = ZorviUrlSearchParams()
        params.append("q name", "a & b")
        assertEquals("q+name=a+%26+b", params.toQueryString())
    }
}
