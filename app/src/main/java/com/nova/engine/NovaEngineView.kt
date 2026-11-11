package com.nova.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * First vertical slice of Nova's own renderer.
 *
 * Network I/O is off the UI thread. The view draws parsed text with Android
 * Canvas. This is a prototype, not a standards-compliant web browser.
 */
class NovaEngineView(context: Context) : View(context) {
    private val worker = Executors.newSingleThreadExecutor()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var blocks: List<HtmlNode> = emptyList()
    private var links: List<Pair<FloatArray, String>> = emptyList()
    private var scrollYpx = 0f
    private var documentHeight = 0f
    var onNavigate: ((String) -> Unit)? = null
    var onPageTitle: ((String) -> Unit)? = null
    var onLoadError: ((String) -> Unit)? = null

    init {
        setBackgroundColor(Color.WHITE)
        isFocusable = true
    }

    fun loadUrl(address: String) {
        val target = runCatching { URL(address) }.getOrNull()
        if (target == null || target.protocol !in setOf("http", "https")) {
            onLoadError?.invoke("Only HTTP and HTTPS are supported in this engine prototype")
            return
        }
        worker.execute {
            try {
                val connection = (target.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12_000
                    readTimeout = 15_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "NovaEngine/0.1 (Android)")
                    setRequestProperty("Accept", "text/html,application/xhtml+xml")
                }
                try {
                    val type = connection.contentType.orEmpty()
                    require(type.contains("text/html", true) || type.contains("application/xhtml+xml", true)) {
                        "This prototype currently displays HTML documents only"
                    }
                    val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val document = HtmlDocument.parse(html)
                    val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)?.trim().orEmpty()
                    post {
                        blocks = document.visibleBlocks()
                        onPageTitle?.invoke(title)
                        scrollYpx = 0f
                        requestLayout()
                        invalidate()
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (error: Exception) {
                post { onLoadError?.invoke(error.message ?: "Page load failed") }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.WHITE)
        links = emptyList()
        var y = 24f - scrollYpx
        val hitTargets = mutableListOf<Pair<FloatArray, String>>()
        blocks.forEach { node ->
            val text = node.text.ifEmpty { node.children.joinToString(" ") { it.text } }
            if (text.isBlank()) return@forEach
            val heading = node.tag in setOf("h1", "h2", "h3", "title")
            val size = when (node.tag) {
                "h1" -> 28f
                "h2" -> 23f
                "h3" -> 20f
                "pre" -> 14f
                else -> 17f
            }
            paint.color = if (node.tag == "a") Color.rgb(20, 90, 190) else Color.rgb(30, 30, 30)
            paint.textSize = size
            paint.typeface = if (heading) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
            val maxWidth = max(80f, width - 32f)
            val words = text.split(Regex("\\s+"))
            var line = ""
            val lineHeight = size * 1.45f
            words.forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                    canvas.drawText(line, 16f, y, paint)
                    y += lineHeight
                    line = word
                } else line = candidate
            }
            if (line.isNotEmpty()) {
                canvas.drawText(line, 16f, y, paint)
                if (node.tag == "a") {
                    val href = node.attributes["href"].orEmpty()
                    if (href.isNotBlank()) hitTargets.add(Pair(floatArrayOf(8f, y - size, width - 8f, y + 6f), href))
                }
                y += lineHeight
            }
            y += if (heading) 18f else 12f
        }
        links = hitTargets
        documentHeight = y + scrollYpx
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val y = event.y + scrollYpx
                val hit = links.firstOrNull { y >= it.first[1] && y <= it.first[3] && event.x >= it.first[0] && event.x <= it.first[2] }
                if (hit != null) {
                    val destination = runCatching { URL(URL("https://invalid.local/"), hit.second).toString() }.getOrNull()
                    if (destination != null && destination.startsWith("https://") || destination?.startsWith("http://") == true) {
                        onNavigate?.invoke(destination)
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // Basic touch scrolling; kinetic scrolling is a later milestone.
                return true
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        worker.shutdownNow()
        super.onDetachedFromWindow()
    }
}
