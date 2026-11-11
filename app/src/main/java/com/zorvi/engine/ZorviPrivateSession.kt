package com.zorvi.engine

/**
 * Owns ephemeral state for one private browsing session.
 *
 * The owner must call [close] when the last private tab/session is closed.
 * No data is written to disk. This is a lifecycle primitive, not a complete
 * private-browsing guarantee: platform caches, downloads, DNS and OS-level
 * telemetry are outside this class.
 */
internal class ZorviPrivateSession(
    private val storage: ZorviPartitionedStorage = ZorviPartitionedStorage(),
    private val httpCache: ZorviMemoryHttpCache = ZorviMemoryHttpCache()
) : AutoCloseable {
    @Volatile
    private var closed = false

    val isClosed: Boolean get() = closed

    @Synchronized
    fun getItem(topLevel: ZorviUrl, origin: ZorviUrl, key: String): String? {
        checkOpen()
        return storage.getItem(topLevel, origin, key)
    }

    @Synchronized
    fun setItem(topLevel: ZorviUrl, origin: ZorviUrl, key: String, value: String) {
        checkOpen()
        storage.setItem(topLevel, origin, key, value)
    }

    @Synchronized
    fun removeItem(topLevel: ZorviUrl, origin: ZorviUrl, key: String) {
        checkOpen()
        storage.removeItem(topLevel, origin, key)
    }

    @Synchronized
    fun clearSite(topLevel: ZorviUrl, origin: ZorviUrl) {
        checkOpen()
        storage.clearOrigin(topLevel, origin)
    }

    @Synchronized
    fun cachedResponse(url: ZorviUrl): ZorviHttpResponse? {
        checkOpen()
        return httpCache.get(url)
    }

    @Synchronized
    fun cacheResponse(response: ZorviHttpResponse, requestMethod: String = "GET") {
        checkOpen()
        httpCache.put(response, requestMethod)
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            storage.clearAll()
            httpCache.clear()
        }
    }

    private fun checkOpen() {
        check(!closed) { "Private session is closed" }
    }
}
