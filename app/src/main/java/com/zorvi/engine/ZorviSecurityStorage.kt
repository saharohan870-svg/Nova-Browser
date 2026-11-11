package com.zorvi.engine

/**
 * Tuple origin for HTTP(S) documents. Opaque origins are deliberately not
 * represented by this prototype; unsupported schemes fail closed.
 */
internal data class ZorviOrigin(
    val scheme: String,
    val host: String,
    val effectivePort: Int
) {
    val serialized: String
        get() = "$scheme://$host" + if ((scheme == "http" && effectivePort == 80) ||
            (scheme == "https" && effectivePort == 443)) "" else ":$effectivePort"

    companion object {
        fun from(url: ZorviUrl): ZorviOrigin {
            val scheme = url.protocol.removeSuffix(":").lowercase()
            require(scheme == "http" || scheme == "https") { "Unsupported origin scheme" }
            val port = if (url.port != -1) url.port else if (scheme == "https") 443 else 80
            return ZorviOrigin(scheme, url.hostname.lowercase(), port)
        }
    }
}

/** Fail-closed same-origin policy helpers. */
internal object ZorviSameOriginPolicy {
    fun isSameOrigin(first: ZorviUrl, second: ZorviUrl): Boolean =
        runCatching { ZorviOrigin.from(first) == ZorviOrigin.from(second) }.getOrDefault(false)

    fun mayReadDocument(requestingDocument: ZorviUrl, target: ZorviUrl): Boolean =
        isSameOrigin(requestingDocument, target)

    fun mayUseNativeBridge(documentUrl: ZorviUrl, committedUrl: ZorviUrl): Boolean =
        isSameOrigin(documentUrl, committedUrl)
}

/**
 * Storage is partitioned by (top-level origin, requesting origin). This initial
 * implementation is memory-only: it deliberately makes no persistence claim.
 * A private instance is discarded with its owning private session.
 */
internal class ZorviPartitionedStorage(
    private val quotaCharsPerOrigin: Int = DEFAULT_QUOTA_CHARS
) {
    init { require(quotaCharsPerOrigin > 0) }

    private data class PartitionKey(val topLevel: ZorviOrigin, val origin: ZorviOrigin)
    private val partitions = mutableMapOf<PartitionKey, LinkedHashMap<String, String>>()

    @Synchronized
    fun getItem(topLevel: ZorviUrl, origin: ZorviUrl, key: String): String? =
        partitions[partitionKey(topLevel, origin)]?.get(key)

    @Synchronized
    fun setItem(topLevel: ZorviUrl, origin: ZorviUrl, key: String, value: String) {
        val partition = partitionKey(topLevel, origin)
        val values = partitions.getOrPut(partition) { linkedMapOf() }
        val previous = values[key]
        val currentSize = values.entries.sumOf { (k, v) -> k.length + v.length }
        val updatedSize = currentSize - (key.length + (previous?.length ?: 0)) + key.length + value.length
        require(updatedSize <= quotaCharsPerOrigin) { "Storage quota exceeded" }
        values[key] = value
    }

    @Synchronized
    fun removeItem(topLevel: ZorviUrl, origin: ZorviUrl, key: String) {
        val partition = partitionKey(topLevel, origin)
        partitions[partition]?.let { values ->
            values.remove(key)
            if (values.isEmpty()) partitions.remove(partition)
        }
    }

    @Synchronized
    fun clearOrigin(topLevel: ZorviUrl, origin: ZorviUrl) {
        partitions.remove(partitionKey(topLevel, origin))
    }

    @Synchronized
    fun clearAll() {
        partitions.clear()
    }

    @Synchronized
    fun keyCount(topLevel: ZorviUrl, origin: ZorviUrl): Int =
        partitions[partitionKey(topLevel, origin)]?.size ?: 0

    private fun partitionKey(topLevel: ZorviUrl, origin: ZorviUrl): PartitionKey =
        PartitionKey(ZorviOrigin.from(topLevel), ZorviOrigin.from(origin))

    companion object {
        const val DEFAULT_QUOTA_CHARS = 256 * 1024
    }
}
