package com.zorvi.engine

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Small, incomplete URL / URLSearchParams foundation; not exposed to page JS yet. */
internal class ZorviUrl(input: String, base: ZorviUrl? = null) {
    private val parsed: URI

    init {
        val candidate = runCatching { URI(input.trim()) }
            .getOrElse { throw IllegalArgumentException("Invalid URL", it) }
        val resolved = (if (candidate.isAbsolute) candidate else {
            val baseUri = base?.parsed ?: throw IllegalArgumentException("Relative URL requires a base URL")
            baseUri.resolve(candidate)
        }).normalize()
        require(resolved.isAbsolute && resolved.isHierarchical) { "URL must be absolute and hierarchical" }
        val scheme = resolved.scheme.lowercase()
        require(scheme == "http" || scheme == "https") { "Only HTTP and HTTPS URLs are supported" }
        require(!resolved.host.isNullOrBlank()) { "URL must contain a host" }
        require(resolved.rawUserInfo == null) { "Credentials in URLs are not supported" }
        parsed = resolved
    }

    val protocol: String get() = parsed.scheme.lowercase() + ":"
    val hostname: String get() = parsed.host.lowercase()
    val port: Int get() = parsed.port
    val pathname: String get() = parsed.rawPath?.ifEmpty { "/" } ?: "/"
    val search: String get() = parsed.rawQuery?.let { "?$it" } ?: ""
    val hash: String get() = parsed.rawFragment?.let { "#$it" } ?: ""
    val origin: String get() {
        val defaultPort = (protocol == "http:" && port == 80) || (protocol == "https:" && port == 443)
        return "${protocol}//$hostname" + if (port == -1 || defaultPort) "" else ":$port"
    }
    val href: String get() = parsed.toASCIIString()
    val searchParams: ZorviUrlSearchParams get() = ZorviUrlSearchParams(parsed.rawQuery.orEmpty())
    override fun toString(): String = href
}

/** Ordered, duplicate-preserving form-query parameter collection. */
internal class ZorviUrlSearchParams(query: String = "") {
    private val entries = mutableListOf<Pair<String, String>>()

    init {
        query.removePrefix("?").takeIf { it.isNotEmpty() }?.split("&")?.forEach { part ->
            if (part.isNotEmpty()) {
                val separator = part.indexOf('=')
                val key = if (separator < 0) part else part.substring(0, separator)
                val value = if (separator < 0) "" else part.substring(separator + 1)
                entries += decode(key) to decode(value)
            }
        }
    }

    fun append(name: String, value: String) { entries += name to value }
    fun delete(name: String) { entries.removeAll { it.first == name } }
    fun get(name: String): String? = entries.firstOrNull { it.first == name }?.second
    fun getAll(name: String): List<String> = entries.filter { it.first == name }.map { it.second }
    fun has(name: String): Boolean = entries.any { it.first == name }

    fun set(name: String, value: String) {
        val first = entries.indexOfFirst { it.first == name }
        entries.removeAll { it.first == name }
        if (first < 0) entries += name to value
        else entries.add(first.coerceAtMost(entries.size), name to value)
    }

    fun toQueryString(): String = entries.joinToString("&") { (name, value) ->
        "${encode(name)}=${encode(value)}"
    }
    override fun toString(): String = toQueryString()

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }
            .getOrElse { throw IllegalArgumentException("Invalid query encoding", it) }
}
