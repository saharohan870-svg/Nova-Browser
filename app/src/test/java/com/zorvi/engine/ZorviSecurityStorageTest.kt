package com.zorvi.engine

import org.junit.Assert.*
import org.junit.Test

class ZorviSecurityStorageTest {
    private fun url(value: String) = ZorviUrl(value)

    @Test fun originsIgnoreDefaultPortsButRespectNonDefaultPorts() {
        assertEquals(
            ZorviOrigin.from(url("https://example.com")),
            ZorviOrigin.from(url("https://example.com:443/path"))
        )
        assertNotEquals(
            ZorviOrigin.from(url("https://example.com")),
            ZorviOrigin.from(url("https://example.com:8443"))
        )
    }

    @Test fun sameOriginRequiresMatchingSchemeHostAndEffectivePort() {
        val base = url("https://example.com/a")
        assertTrue(ZorviSameOriginPolicy.isSameOrigin(base, url("https://example.com/b")))
        assertFalse(ZorviSameOriginPolicy.isSameOrigin(base, url("http://example.com/b")))
        assertFalse(ZorviSameOriginPolicy.isSameOrigin(base, url("https://sub.example.com/b")))
        assertFalse(ZorviSameOriginPolicy.isSameOrigin(base, url("https://example.com:8443/b")))
    }

    @Test fun nativeBridgeCheckFailsClosedAcrossOrigins() {
        assertTrue(ZorviSameOriginPolicy.mayUseNativeBridge(
            url("https://example.com/"), url("https://example.com/next")
        ))
        assertFalse(ZorviSameOriginPolicy.mayUseNativeBridge(
            url("https://example.com/"), url("https://evil.example/")
        ))
    }

    @Test fun storageIsPartitionedByTopLevelAndRequestingOrigin() {
        val store = ZorviPartitionedStorage()
        val topA = url("https://shop.example/")
        val topB = url("https://news.example/")
        val thirdParty = url("https://tracker.example/")
        store.setItem(topA, thirdParty, "id", "A")
        store.setItem(topB, thirdParty, "id", "B")
        assertEquals("A", store.getItem(topA, thirdParty, "id"))
        assertEquals("B", store.getItem(topB, thirdParty, "id"))
    }

    @Test fun sameOriginUnderSameTopLevelSharesStorage() {
        val store = ZorviPartitionedStorage()
        val top = url("https://example.com/")
        store.setItem(top, url("https://example.com/a"), "theme", "dark")
        assertEquals("dark", store.getItem(top, url("https://example.com/b"), "theme"))
    }

    @Test fun quotaIsEnforcedAndReplacingValueDoesNotDoubleCount() {
        val store = ZorviPartitionedStorage(quotaCharsPerOrigin = 8)
        val top = url("https://example.com/")
        store.setItem(top, top, "a", "1234")
        store.setItem(top, top, "a", "12345")
        try {
            store.setItem(top, top, "b", "123")
            fail("Expected quota rejection")
        } catch (_: IllegalArgumentException) { }
        assertEquals("12345", store.getItem(top, top, "a"))
    }

    @Test fun clearOriginAndClearAllRemoveData() {
        val store = ZorviPartitionedStorage()
        val top = url("https://example.com/")
        store.setItem(top, top, "a", "1")
        store.clearOrigin(top, top)
        assertNull(store.getItem(top, top, "a"))
        store.setItem(top, top, "b", "2")
        store.clearAll()
        assertEquals(0, store.keyCount(top, top))
    }
}
