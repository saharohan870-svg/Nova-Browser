package com.zorvi.browser

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.zorvi.engine.ZorviEngineView
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/**
 * Explicit experimental-engine page host.
 *
 * Pages in this Activity are rendered by ZorviEngineView; this Activity does not
 * create or delegate to Android WebView. The normal browser shell remains available
 * as a separately labelled compatibility path.
 */
class ZorviPageActivity : Activity() {
    private lateinit var engineView: ZorviEngineView
    private lateinit var address: EditText
    private lateinit var title: TextView
    private val backStack = ArrayList<String>()
    private var currentUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildPageUi()
        val initial = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (initial.isNotBlank()) navigate(initial, addHistory = false)
    }

    private fun buildPageUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6, 4, 6, 4)
        }
        toolbar.addView(action("←") { goBack() })
        address = EditText(this).apply {
            isSingleLine = true
            textSize = 14f
            hint = "Search or enter address"
            imeOptions = EditorInfo.IME_ACTION_GO
            setSelectAllOnFocus(true)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                    navigateInput(text.toString())
                    true
                } else false
            }
        }
        toolbar.addView(address, LinearLayout.LayoutParams(0, 48, 1f))
        toolbar.addView(action("Go") { navigateInput(address.text.toString()) })
        toolbar.addView(action("↻") { currentUrl?.let { navigate(it, addHistory = false) } })
        root.addView(toolbar)

        val engineLabel = TextView(this).apply {
            text = "Zorvi Engine · Experimental renderer"
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(10, 5, 10, 5)
        }
        root.addView(engineLabel)
        title = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(10, 4, 10, 4)
            visibility = View.GONE
        }
        root.addView(title)

        engineView = ZorviEngineView(this).apply {
            onNavigate = { destination -> navigate(destination) }
            onPageTitle = { pageTitle ->
                runOnUiThread {
                    title.text = pageTitle
                    title.visibility = if (pageTitle.isBlank()) View.GONE else View.VISIBLE
                }
            }
            onLoadError = { message ->
                runOnUiThread { Toast.makeText(this@ZorviPageActivity, message, Toast.LENGTH_LONG).show() }
            }
        }
        root.addView(engineView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun action(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        minWidth = 0
        setPadding(5, 0, 5, 0)
        setOnClickListener { click() }
    }

    private fun navigateInput(input: String) {
        val value = input.trim()
        if (value.isEmpty()) return
        val uri = runCatching { URI(value) }.getOrNull()
        val scheme = uri?.scheme?.lowercase(Locale.ROOT)
        val destination = when {
            scheme == "http" || scheme == "https" -> value
            scheme != null -> {
                Toast.makeText(this, "Zorvi supports HTTP and HTTPS pages only", Toast.LENGTH_SHORT).show()
                return
            }
            value.contains(".") && !value.any(Char::isWhitespace) -> "https://$value"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(value, "UTF-8")
        }
        navigate(destination)
    }

    private fun navigate(destination: String, addHistory: Boolean = true) {
        val parsed = runCatching { URI(destination) }.getOrNull()
        if (parsed == null || parsed.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || parsed.host.isNullOrBlank()) {
            Toast.makeText(this, "Invalid web address", Toast.LENGTH_SHORT).show()
            return
        }
        if (addHistory && currentUrl != null && currentUrl != destination) backStack.add(currentUrl!!)
        currentUrl = destination
        address.setText(destination)
        title.text = "Loading…"
        title.visibility = View.VISIBLE
        engineView.loadUrl(destination)
    }

    private fun goBack() {
        val previous = backStack.removeLastOrNull()
        if (previous == null) {
            finish()
        } else {
            currentUrl = null
            navigate(previous, addHistory = false)
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        goBack()
    }

    companion object {
        const val EXTRA_URL = "com.zorvi.browser.extra.ZORVI_URL"
    }
}
