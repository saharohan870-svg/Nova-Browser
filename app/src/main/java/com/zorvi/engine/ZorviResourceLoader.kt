package com.zorvi.engine

import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal enum class ZorviResourceType { DOCUMENT, STYLESHEET, SCRIPT, IMAGE, FONT, MEDIA, OTHER }

/** Asynchronous resource loader with bounded worker count and cancellation of active calls. */
internal class ZorviResourceLoader(
    private val cache: ZorviMemoryHttpCache = ZorviMemoryHttpCache(),
    maxConcurrentRequests: Int = 4
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val executor = Executors.newFixedThreadPool(maxConcurrentRequests.coerceIn(1, 8)) { task ->
        Thread(task, "Zorvi-Network").apply { isDaemon = true }
    }

    suspend fun load(
        url: ZorviUrl,
        type: ZorviResourceType = ZorviResourceType.OTHER,
        headers: Map<String, String> = emptyMap(),
        maxResponseBytes: Int = ZorviHttpRequest.DEFAULT_MAX_RESPONSE_BYTES
    ): ZorviHttpResponse {
        check(!closed.get()) { "Resource loader is closed" }
        if (headers.isEmpty()) cache.get(url)?.let { return it }
        return suspendCancellableCoroutine { continuation ->
            val call = ZorviHttpCall(
                ZorviHttpRequest(url, headers = mapOf("Accept" to acceptFor(type)) + headers,
                    maxResponseBytes = maxResponseBytes)
            )
            continuation.invokeOnCancellation { call.cancel() }
            try {
                executor.execute {
                    if (!continuation.isActive || closed.get()) {
                        call.cancel()
                        return@execute
                    }
                    try {
                        val response = call.execute()
                        if (response.statusCode == 200 && headers.isEmpty()) cache.put(response)
                        if (continuation.isActive) continuation.resume(response)
                    } catch (failure: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
                }
            } catch (failure: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            }
        }
    }

    private fun acceptFor(type: ZorviResourceType): String = when (type) {
        ZorviResourceType.DOCUMENT -> "text/html,application/xhtml+xml"
        ZorviResourceType.STYLESHEET -> "text/css,*/*;q=0.1"
        ZorviResourceType.SCRIPT -> "*/*"
        ZorviResourceType.IMAGE -> "image/avif,image/webp,image/*,*/*;q=0.8"
        ZorviResourceType.FONT -> "font/woff2,font/woff,*/*;q=0.1"
        ZorviResourceType.MEDIA -> "audio/*,video/*,*/*;q=0.5"
        ZorviResourceType.OTHER -> "*/*"
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow()
            cache.clear()
        }
    }
}
