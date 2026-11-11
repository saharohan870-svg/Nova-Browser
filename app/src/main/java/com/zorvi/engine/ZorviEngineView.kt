package com.zorvi.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/**
 * First vertical slice of Zorvi's own renderer.
 *
 * Network I/O is off the UI thread. The view draws parsed text with Android
 * Canvas. This is a prototype, not a standards-compliant web browser.
 */
class ZorviEngineView(context: Context) : View(context) {
    private val worker = Executors.newSingleThreadExecutor()
    private val navigationGeneration = AtomicInteger(0)
    private val activeRequest = AtomicReference<ZorviHttpCall?>(null)
    private var lastTouchY = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var blocks: List<HtmlNode> = emptyList()
    private var links: List<Pair<FloatArray, String>> = emptyList()
    private var scrollYpx = 0f
    private var documentHeight = 0f
    private var styleSheet = ZorviCssStyleSheet("")
    private var pageUrl: URL? = null
    private var domDocument: ZorviDomDocument? = null
    private var accessibilityTree = ZorviAccessibilityTree(emptyList())
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
        val target = runCatching { ZorviUrl(address) }.getOrNull()
        if (target == null || target.protocol !in setOf("http:", "https:")) {
            onLoadError?.invoke("Only HTTP and HTTPS are supported in Zorvi")
            return
        }
        val generation = navigationGeneration.incrementAndGet()
        activeRequest.getAndSet(null)?.cancel()
        post {
            blocks = emptyList()
            links = emptyList()
            scrollYpx = 0f
            invalidate()
        }
        worker.execute {
            val call = ZorviHttpCall(
                ZorviHttpRequest(
                    target,
                    headers = mapOf("Accept" to "text/html,application/xhtml+xml"),
                    maxResponseBytes = ZorviHttpRequest.DEFAULT_MAX_RESPONSE_BYTES
                )
            )
            activeRequest.set(call)
            try {
                val response = call.execute()
                if (generation != navigationGeneration.get()) return@execute
                require(response.statusCode in 200..299) { "HTTP ${response.statusCode}" }
                val type = response.header("Content-Type").orEmpty()
                require(type.contains("text/html", true) || type.contains("application/xhtml+xml", true)) {
                    "Zorvi currently displays HTML documents only"
                }
                val charset = Regex("charset=([^; ]+)", RegexOption.IGNORE_CASE)
                    .find(type)?.groupValues?.get(1)?.trim('"', '\'')
                val encoding = runCatching { java.nio.charset.Charset.forName(charset ?: "UTF-8") }
                    .getOrDefault(Charsets.UTF_8)
                val html = response.body.toString(encoding)
                val document = HtmlDocument.parse(html)
                val css = document.findAll("style").joinToString("\\n") { document.textContent(it) }
                val scripts = document.findAll("script")
                    .filter { it.attributes["src"].isNullOrBlank() }
                    .take(32)
                    .map { document.textContent(it).take(64 * 1024) }
                    .filter { it.isNotBlank() }
                val baseUrl = java.net.URL(response.url.href)
                val title = document.findFirst("title")?.let(document::textContent)?.trim().orEmpty()
                post {
                    domDocument = ZorviDomDocument.from(document)
                    domDocument?.onMutation = {
                        post {
                            blocks = domDocument?.visibleBlocks().orEmpty()
                            refreshAccessibility()
                            invalidate()
                        }
                    }
                    blocks = domDocument?.visibleBlocks().orEmpty()
                    accessibilityTree = ZorviAccessibilityTree.from(document)
                    refreshAccessibility()
                    styleSheet = ZorviCssStyleSheet(css)
                    pageUrl = baseUrl
                    domDocument?.let { scriptRuntime.loadDocument(it, scripts) }
                    onPageTitle?.invoke(title)
                    scrollYpx = 0f
                    requestLayout()
                    invalidate()
                }
            } catch (error: Exception) {
                if (generation == navigationGeneration.get()) {
                    post {
                        if (generation == navigationGeneration.get()) onLoadError?.invoke(error.message ?: "Page load failed")
                    }
                }
            } finally {
                activeRequest.compareAndSet(call, null)
            }
        }
    }

    private fun refreshAccessibility() {
        val labels = accessibilityTree.nodes.map { node ->
            val role = node.role.replace('-', ' ')
            if (role.isBlank()) node.label else "$role: ${node.label}"
        }
        contentDescription = labels.joinToString(". ").ifBlank { "Web page" }
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
            MotionEvent.ACTION_DOWN -> { lastTouchY = event.y; return true }
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
                val delta = lastTouchY - event.y
                lastTouchY = event.y
                scrollYpx = (scrollYpx + delta).coerceIn(0f, max(0f, documentHeight - height))
                invalidate()
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
