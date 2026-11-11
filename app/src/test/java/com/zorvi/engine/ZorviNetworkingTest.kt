package com.zorvi.engine

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ZorviNetworkingTest {
    private lateinit var server: HttpServer
    private lateinit var base: String

    @Before fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ok") { exchange ->
            val bytes = "hello-zorvi".toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
            exchange.responseHeaders.add("Cache-Control", "public, max-age=60")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/ok")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/large") { exchange ->
            val bytes = ByteArray(4096) { 65 }
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
    }

    @After fun stopServer() { server.stop(0) }

    @Test fun fetchesResponseBodyAndHeaders() {
        val response = ZorviHttpCall(ZorviHttpRequest(ZorviUrl("$base/ok"))).execute()
        assertEquals(200, response.statusCode)
        assertEquals("hello-zorvi", response.body.toString(Charsets.UTF_8))
        assertEquals("text/plain; charset=utf-8", response.header("Content-Type"))
    }

    @Test fun followsRelativeRedirect() {
        val response = ZorviHttpCall(ZorviHttpRequest(ZorviUrl("$base/redirect"))).execute()
        assertEquals(200, response.statusCode)
        assertTrue(response.url.href.endsWith("/ok"))
    }

    @Test fun rejectsOversizedResponse() {
        try {
            ZorviHttpCall(ZorviHttpRequest(ZorviUrl("$base/large"), maxResponseBytes = 100)).execute()
            fail("Expected response size limit")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("size limit"))
        }
    }

    @Test fun cachesOnlyExplicitFreshResponses() {
        val cache = ZorviMemoryHttpCache(1024)
        try {
            val url = ZorviUrl("$base/ok")
            cache.put(ZorviHttpCall(ZorviHttpRequest(url)).execute())
            assertTrue(cache.get(url)?.fromCache == true)
            assertNull(cache.get(ZorviUrl("$base/not-cached")))
        } finally { cache.clear() }
    }

    @Test fun expiresCachedEntries() {
        val cache = ZorviMemoryHttpCache(1024)
        try {
            val url = ZorviUrl("$base/ok")
            cache.put(ZorviHttpCall(ZorviHttpRequest(url)).execute(), nowMs = 1000L)
            assertNull(cache.get(url, nowMs = 62_000L))
        } finally { cache.clear() }
    }

    @Test fun refusesToCacheNoStoreResponses() {
        val cache = ZorviMemoryHttpCache(1024)
        val url = ZorviUrl("$base/ok")
        try {
            val response = ZorviHttpCall(ZorviHttpRequest(url)).execute()
            cache.put(response.copy(headers = mapOf("Cache-Control" to listOf("no-store, max-age=60"))))
            assertNull(cache.get(url))
        } finally { cache.clear() }
    }
}
