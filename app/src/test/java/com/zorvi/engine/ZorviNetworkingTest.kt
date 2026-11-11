package com.zorvi.engine

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ZorviNetworkingTest {
    private lateinit var server: ServerSocket
    private lateinit var serverThread: Thread
    private lateinit var base: String
    @Volatile private var serverFailure: Throwable? = null

    @Before fun startServer() {
        server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        base = "http://127.0.0.1:${server.localPort}"
        serverThread = Thread {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val reader = BufferedReader(
                            InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1)
                        )
                        val requestLine = reader.readLine().orEmpty()
                        while (true) {
                            val header = reader.readLine() ?: break
                            if (header.isEmpty()) break
                        }
                        val path = requestLine.split(' ').getOrNull(1).orEmpty()
                        val (status, reason, headers, body) = when (path) {
                            "/ok" -> Quad(
                                200, "OK",
                                "Content-Type: text/plain; charset=utf-8\r\nCache-Control: public, max-age=60\r\n",
                                "hello-zorvi".toByteArray(StandardCharsets.UTF_8)
                            )
                            "/redirect" -> Quad(302, "Found", "Location: /ok\r\n", ByteArray(0))
                            "/large" -> Quad(200, "OK", "", ByteArray(4096) { 65 })
                            else -> Quad(404, "Not Found", "", ByteArray(0))
                        }
                        val output = socket.getOutputStream()
                        val head = "HTTP/1.1 $status $reason\r\n$Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                        output.write(head.toByteArray(StandardCharsets.ISO_8859_1))
                        output.write(body)
                        output.flush()
                    }
                } catch (closed: java.net.SocketException) {
                    if (!server.isClosed) serverFailure = closed
                } catch (failure: Throwable) {
                    serverFailure = failure
                }
            }
        }.apply { name = "zorvi-test-http-server"; isDaemon = true; start() }
    }

    @After fun stopServer() {
        server.close()
        serverThread.join(1_000)
        serverFailure?.let { throw AssertionError("Test HTTP server failed", it) }
    }

    private data class Quad(
        val status: Int,
        val reason: String,
        val headers: String,
        val body: ByteArray
    )

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
