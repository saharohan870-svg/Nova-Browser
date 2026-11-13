package com.zorvi.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTabStateTest {
    @Test fun roundTripsTabsAndActiveIndex() {
        val input = listOf(SavedBrowserTab("https://example.com", "Work"), SavedBrowserTab("about:blank", "Personal"))
        val decoded = BrowserTabState.decode(BrowserTabState.encode(input, 1))
        assertEquals(input, decoded.first)
        assertEquals(1, decoded.second)
    }

    @Test fun preservesDistinctGroupAssignments() {
        val input = listOf(SavedBrowserTab("https://work.test", "Work"), SavedBrowserTab("https://home.test", "Personal"))
        assertEquals(input, BrowserTabState.decode(BrowserTabState.encode(input, 0)).first)
    }

    @Test fun rejectsUnsafeSchemesAndMalformedState() {
        val decoded = BrowserTabState.decode("""{"tabs":[{"url":"file:///secret"},{"url":"javascript:alert(1)"},{"url":"https://safe.test"}],"active":9}""")
        assertEquals(listOf(SavedBrowserTab("https://safe.test", "Default")), decoded.first)
        assertEquals(0, decoded.second)
        assertTrue(BrowserTabState.decode("not-json").first.isEmpty())
    }
}
