package com.zorvi.engine

import org.junit.Assert.*
import org.junit.Test

class ZorviPrivateSessionTest {
    private fun url(value: String) = ZorviUrl(value)

    @Test fun closingSessionErasesStorageAndCache() {
        val storage = ZorviPartitionedStorage()
        val cache = ZorviMemoryHttpCache()
        val session = ZorviPrivateSession(storage, cache)
        val page = url("https://private.example/")
        session.setItem(page, page, "token", "ephemeral")
        session.cacheResponse(
            ZorviHttpResponse(
                url = page,
                statusCode = 200,
                headers = mapOf("Cache-Control" to listOf("public, max-age=60")),
                body = byteArrayOf(1, 2, 3)
            )
        )

        assertEquals("ephemeral", session.getItem(page, page, "token"))
        assertNotNull(session.cachedResponse(page))

        session.close()

        assertTrue(session.isClosed)
        assertNull(storage.getItem(page, page, "token"))
        assertNull(cache.get(page))
    }

    @Test fun closingIsIdempotent() {
        val session = ZorviPrivateSession()
        session.close()
        session.close()
        assertTrue(session.isClosed)
    }

    @Test fun closedSessionRejectsStorageReadsAndWrites() {
        val session = ZorviPrivateSession()
        val page = url("https://private.example/")
        session.close()

        assertClosed { session.getItem(page, page, "x") }
        assertClosed { session.setItem(page, page, "x", "y") }
        assertClosed { session.removeItem(page, page, "x") }
        assertClosed { session.clearSite(page, page) }
    }

    @Test fun closedSessionRejectsCacheAccess() {
        val session = ZorviPrivateSession()
        val page = url("https://private.example/")
        session.close()

        assertClosed { session.cachedResponse(page) }
        assertClosed {
            session.cacheResponse(
                ZorviHttpResponse(page, 200, emptyMap(), byteArrayOf(1))
            )
        }
    }

    private fun assertClosed(block: () -> Unit) {
        try {
            block()
            fail("Expected closed-session rejection")
        } catch (expected: IllegalStateException) {
            assertEquals("Private session is closed", expected.message)
        }
    }
}
