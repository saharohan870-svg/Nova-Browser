package com.zorvi.engine

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

internal data class ZorviHttpRequest(
    val url: ZorviUrl,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val maxResponseBytes: Int = 5 * 1024 * 1024
) {
    init {
        require(method.matches(Regex("[A-Z]{1,16}")))
        require(maxResponseBytes in 1..(20 * 1024 * 1024))
        headers.forEach { (name, value) ->
            require(name.matches(Regex("[!#$%&'*+.^_\u0060|~0-9A-Za-z-]+")))
            require('\r' !in value && '\n' !in value)
        }
    }
    companion object { const val DEFAULT_MAX_RESPONSE_BYTES = 5 * 1024 * 1024 }
}

internal data class ZorviHttpResponse(
    val url: ZorviUrl,
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val body: ByteArray,
    val fromCache: Boolean = false
) {
    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()
}

/** HTTP/1.1 transport. TLS uses the Android/JVM platform trust store; certificate failures propagate. */
internal class ZorviHttpCall(
    private val request: ZorviHttpRequest,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
    private val maxRedirects: Int = 10
) {
    private val cancelled = AtomicBoolean(false)
    private val active = AtomicReference<HttpURLConnection?>(null)

    fun cancel() {
        cancelled.set(true)
        active.getAndSet(null)?.disconnect()
    }

    fun execute(): ZorviHttpResponse {
        var url = request.url
        var method = request.method
        var body = request.body
        var headers = request.headers
        var redirects = 0
        while (true) {
            check(!cancelled.get()) { "Request cancelled" }
            val connection = URL(url.href).openConnection() as HttpURLConnection
            active.set(connection)
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = connectTimeoutMs
                connection.readTimeout = readTimeoutMs
                connection.requestMethod = method
                connection.setRequestProperty("User-Agent", "ZorviEngine/0.1")
                connection.setRequestProperty("Accept-Encoding", "gzip, deflate")
                headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
                if (body != null && method !in setOf("GET", "HEAD")) {
                    connection.doOutput = true
                    connection.outputStream.use { it.write(body) }
                }
                check(!cancelled.get()) { "Request cancelled" }
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location")
                        ?: error("Redirect without Location header")
                    if (++redirects > maxRedirects) error("Too many redirects")
                    val next = ZorviUrl(location, url)
                    if (url.protocol == "https:" && next.protocol != "https:")
                        throw SecurityException("HTTPS downgrade redirect blocked")
                    if (status == 303 || ((status == 301 || status == 302) && method == "POST")) {
                        method = "GET"; body = null
                        headers = headers.filterKeys { !it.equals("Content-Type", true) && !it.equals("Content-Length", true) }
                    }
                    if (url.origin != next.origin) {
                        headers = headers.filterKeys {
                            !it.equals("Authorization", true) && !it.equals("Proxy-Authorization", true) &&
                                !it.equals("Cookie", true)
                        }
                    }
                    url = next
                    continue
                }
                val responseHeaders = connection.headerFields.filterKeys { it != null }
                    .mapKeys { it.key!! }.mapValues { it.value.orEmpty().toList() }
                val source = if (status >= 400) connection.errorStream else connection.inputStream
                val encoding = responseHeaders.entries.firstOrNull { it.key.equals("Content-Encoding", true) }
                    ?.value?.firstOrNull()?.lowercase(Locale.ROOT)
                val decoded = when (encoding) {
                    "gzip" -> source?.let(::GZIPInputStream)
                    "deflate" -> source?.let(::InflaterInputStream)
                    null, "", "identity" -> source
                    else -> error("Unsupported content encoding")
                }
                val bytes = decoded?.use { readBounded(it, request.maxResponseBytes) } ?: ByteArray(0)
                return ZorviHttpResponse(url, status, responseHeaders, bytes)
            } finally {
                active.compareAndSet(connection, null)
                connection.disconnect()
            }
        }
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 16 * 1024))
        val buffer = ByteArray(8192)
        while (true) {
            check(!cancelled.get()) { "Request cancelled" }
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > limit) error("Response exceeds size limit")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}

/** Per-loader LRU memory cache; only explicitly fresh public GET responses are retained. */
internal class ZorviMemoryHttpCache(private val maxBytes: Int = 4 * 1024 * 1024) {
    private data class Entry(val response: ZorviHttpResponse, val expiresAt: Long, val size: Int)
    private val entries = LinkedHashMap<String, Entry>(16, .75f, true)
    private var bytes = 0

    @Synchronized fun get(url: ZorviUrl, nowMs: Long = System.currentTimeMillis()): ZorviHttpResponse? {
        val entry = entries[url.href] ?: return null
        if (entry.expiresAt <= nowMs) {
            entries.remove(url.href); bytes -= entry.size; return null
        }
        return entry.response.copy(fromCache = true)
    }

    @Synchronized fun put(response: ZorviHttpResponse, requestMethod: String = "GET", nowMs: Long = System.currentTimeMillis()) {
        if (requestMethod != "GET" || response.statusCode != 200) return
        val directives = response.header("Cache-Control").orEmpty().lowercase(Locale.ROOT)
        if (listOf("no-store", "no-cache", "private").any { it in directives }) return
        val age = Regex("(?:^|,)\\s*max-age=(\\d+)").find(directives)?.groupValues?.get(1)?.toLongOrNull() ?: return
        if (age <= 0) return
        val size = response.body.size + response.headers.entries.sumOf { it.key.length + it.value.sumOf(String::length) }
        if (size > maxBytes) return
        entries.remove(response.url.href)?.let { bytes -= it.size }
        while (bytes + size > maxBytes && entries.isNotEmpty()) {
            val eldest = entries.entries.iterator().next()
            bytes -= eldest.value.size
            entries.remove(eldest.key)
        }
        entries[response.url.href] = Entry(response.copy(body = response.body.copyOf()), nowMs + age * 1000, size)
        bytes += size
    }

    @Synchronized fun clear() { entries.clear(); bytes = 0 }
}
