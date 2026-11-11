package com.zorvi.engine

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
 * First vertical slice of Zorvi's own renderer.
 *
 * Network I/O is off the UI thread. The view draws parsed text with Android
 * Canvas. This is a prototype, not a standards-compliant web browser.
 */
class ZorviEngineView(context: Context) : View(context) {
    private val worker = Executors.newSingleThreadExecutor()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var blocks: List<HtmlNode> = emptyList()
    private var links: List<Pair<FloatArray, String>> = emptyList()
    private var scrollYpx = 0f
    private var documentHeight = 0f
    private var styleSheet = ZorviCssStyleSheet("")
    private var pageUrl: URL? = null
    private var domDocument: ZorviDomDocument? = null
    private val scriptRuntime = ZorviScriptRuntime()
    private val engineEvents = ZorviEventTarget()
    var onDomEvent: ((ZorviEngineEvent) -> Unit)? = null
    var onNavigate: ((String) -> Unit)? = null
    var onPageTitle: ((String) -> Unit)? = null
    var onLoadError: ((String) -> Unit)? = null

    init {
        setBackgroundColor(Color.WHITE)
        isFocusable = true
    }

    fun addEventListener(type: String, handler: (ZorviEngineEvent) -> Unit) = engineEvents.addEventListener(type, handler)

    fun removeEventListener(type: String, handler: (ZorviEngineEvent) -> Unit) = engineEvents.removeEventListener(type, handler)

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
                    setRequestProperty("User-Agent", "ZorviEngine/0.1 (Android)")
                    setRequestProperty("Accept", "text/html,application/xhtml+xml")
                }
                try {
                    val type = connection.contentType.orEmpty()
                    require(type.contains("text/html", true) || type.contains("application/xhtml+xml", true)) {
                        "This prototype currently displays HTML documents only"
                    }
                    val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val document = HtmlDocument.parse(html)
                    val css = document.findAll("style").joinToString("\\n") { document.textContent(it) }
                    val scripts = document.findAll("script")
                        .filter { it.attributes["src"].isNullOrBlank() }
                        .take(32)
                        .map { document.textContent(it).take(64 * 1024) }
                        .filter { it.isNotBlank() }
                    val baseUrl = connection.url
                    val title = document.findFirst("title")?.let(document::textContent)?.trim().orEmpty()
                    post {
                        domDocument = ZorviDomDocument.from(document)
                        domDocument?.onMutation = {
                            post {
                                blocks = domDocument?.visibleBlocks().orEmpty()
                                invalidate()
                            }
                        }
                        blocks = domDocument?.visibleBlocks().orEmpty()
                        styleSheet = ZorviCssStyleSheet(css)
                        pageUrl = baseUrl
                        domDocument?.let { scriptRuntime.loadDocument(it, scripts) }
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
        var y = 16f - scrollYpx
        val hitTargets = mutableListOf<Pair<FloatArray, String>>()
        blocks.forEach { node ->
            val text = node.text.ifEmpty { node.children.joinToString(" ") { it.text } }
            if (text.isBlank()) return@forEach
            val style = styleSheet.styleFor(node)
            if (style.display == "none") return@forEach
            val left = 16f + style.padding
            val right = width - 16f - style.padding
            y += style.marginTop
            val size = style.fontSizeSp * resources.displayMetrics.scaledDensity
            paint.color = style.backgroundColor
            if (style.backgroundColor != Color.TRANSPARENT) {
                canvas.drawRect(16f, y - size, width - 16f, y + size * 0.45f + style.padding * 2, paint)
            }
            paint.color = style.color
            paint.textSize = size
            paint.typeface = if (style.bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
            val maxWidth = max(80f, right - left)
            val words = text.split(Regex("\\s+"))
            var line = ""
            val lineHeight = size * 1.45f
            var firstLineY = y
            fun drawLine(value: String) {
                val measured = paint.measureText(value)
                val x = when (style.textAlign) {
                    "center" -> (width - measured) / 2f
                    "right" -> right - measured
                    else -> left
                }
                canvas.drawText(value, x, y, paint)
                y += lineHeight
            }
            words.forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                    drawLine(line)
                    line = word
                } else line = candidate
            }
            if (line.isNotEmpty()) {
                val before = y
                drawLine(line)
                if (node.tag == "a") {
                    val href = node.attributes["href"].orEmpty()
                    if (href.isNotBlank()) {
                        hitTargets.add(Pair(floatArrayOf(left, firstLineY - size, right, y + 4f), href))
                    }
                }
            }
            y += style.padding + style.marginBottom
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
                    val destination = runCatching { URL(pageUrl, hit.second).toString() }.getOrNull()
                    if (destination != null && (destination.startsWith("https://") || destination.startsWith("http://"))) {
                        val event = ZorviEngineEvent("click", payload = destination)
                        engineEvents.dispatch(event)
                        onDomEvent?.invoke(event)
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
        scriptRuntime.close()
        super.onDetachedFromWindow()
    }
}
