package com.nova.browser

import android.app.*
import android.os.Bundle
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.view.*
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import org.json.JSONArray
import java.net.URLEncoder
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var address: EditText
    private lateinit var engine: Spinner
    private lateinit var tabsLabel: TextView
    private val pages = mutableListOf<WebView>()
    private val privateTabs = mutableSetOf<WebView>()
    private var active = 0
    private var privateMode = false
    private var dark = false
    private val prefs by lazy { getSharedPreferences("nova", MODE_PRIVATE) }
    private val engines = listOf("Google", "Bing", "DuckDuckGo", "Brave Search")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        newTab("https://www.google.com")
    }

    private fun buildUi() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(8,8,8,8) }
        engine = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, engines)
            setSelection(prefs.getInt("engine", 0).coerceIn(0, engines.lastIndex))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { prefs.edit().putInt("engine", position).apply() }
            }
        }
        top.addView(engine, LinearLayout.LayoutParams(112, 48))
        address = EditText(this).apply {
            singleLine = true; hint = "Search or enter address"; textSize = 14f
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) { navigate(text.toString()); true } else false }
        }
        top.addView(address, LinearLayout.LayoutParams(0, 48, 1f))
        top.addView(button("Go") { navigate(address.text.toString()) })
        root.addView(top)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(4,2,4,4) }
        controls.addView(button("←") { current()?.goBack() })
        controls.addView(button("→") { current()?.goForward() })
        controls.addView(button("↻") { current()?.reload() })
        controls.addView(button("★") { bookmark() })
        tabsLabel = TextView(this).apply { text = "Tabs 1"; gravity = Gravity.CENTER; setPadding(8,12,8,12); setOnClickListener { showTabs() } }
        controls.addView(tabsLabel)
        controls.addView(button("Private") { togglePrivate() })
        controls.addView(button("☾") { toggleDark() })
        root.addView(controls)
        setContentView(root)
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 12f; setPadding(5,0,5,0); setOnClickListener { action() }
    }

    private fun current() = pages.getOrNull(active)

    private fun newTab(url: String = "about:blank") {
        val web = WebView(this)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            if (android.os.Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = true
            if (privateMode) cacheMode = WebSettings.LOAD_NO_CACHE
        }
        if (privateMode) privateTabs.add(web)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, !privateMode)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return true
                if (scheme == "http" || scheme == "https") return false
                if (scheme == "about" || scheme == "blob" || scheme == "data") return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "No app can open this link", Toast.LENGTH_SHORT).show()
                    true
                }
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (view === current()) address.setText(url ?: "")
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (view === current()) address.setText(url ?: "")
                if (view !in privateTabs && url?.startsWith("https://") == true) saveHistory(url)
            }
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val host = request?.url?.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return null
                if (blockedHosts.any { host == it || host.endsWith(".$it") }) {
                    return WebResourceResponse("text/plain", "utf-8", 204, "Blocked", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                handler?.cancel()
            }
        }
        web.setDownloadListener { url, userAgent, disposition, mime, _ ->
            try {
                val req = DownloadManager.Request(Uri.parse(url))
                req.setMimeType(mime)
                req.addRequestHeader("User-Agent", userAgent ?: "")
                CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
                req.setTitle(URLUtil.guessFileName(url, disposition, mime))
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Unable to start download", Toast.LENGTH_SHORT).show()
            }
        }
        pages.add(web); active = pages.lastIndex
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        showOnlyActive()
        updateTabs()
        if (url != "about:blank") web.loadUrl(url)
    }

    private fun showOnlyActive() {
        root.removeViews(2, root.childCount - 2)
        pages.forEachIndexed { i, web ->
            web.visibility = if (i == active) View.VISIBLE else View.GONE
            root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    private fun navigate(input: String) {
        val value = input.trim()
        if (value.isEmpty()) return
        val uri = runCatching { Uri.parse(value) }.getOrNull()
        val hasScheme = uri?.scheme?.matches(Regex("(?i)^[a-z][a-z0-9+.-]*$")) == true
        val looksLikeHost = value.contains(".") && !value.any { it.isWhitespace() } && !value.startsWith(".")
        val url = when {
            hasScheme && uri?.scheme in listOf("http", "https") -> value
            hasScheme -> {
                Toast.makeText(this, "Only web addresses can be entered here", Toast.LENGTH_SHORT).show()
                return
            }
            looksLikeHost -> "https://$value"
            else -> {
                val q = URLEncoder.encode(value, "UTF-8")
                when (engine.selectedItemPosition) {
                    1 -> "https://www.bing.com/search?q=$q"
                    2 -> "https://duckduckgo.com/?q=$q"
                    3 -> "https://search.brave.com/search?q=$q"
                    else -> "https://www.google.com/search?q=$q"
                }
            }
        }
        current()?.loadUrl(url)
    }

    private fun updateTabs() { tabsLabel.text = "Tabs ${pages.size}" }

    private fun showTabs() {
        val names = pages.mapIndexed { i, w -> "${i + 1}. ${w.title ?: w.url ?: "New tab"}" }.toMutableList()
        names.add("＋ New tab")
        AlertDialog.Builder(this).setTitle("Nova tabs").setItems(names.toTypedArray()) { _, which ->
            if (which == pages.size) newTab("https://www.google.com") else {
                active = which; showOnlyActive(); updateTabs(); address.setText(current()?.url ?: "")
            }
        }.setNegativeButton("Close current") { _, _ -> closeTab() }.show()
    }

    private fun closeTab() {
        if (pages.size <= 1) {
            current()?.apply { stopLoading(); loadUrl("about:blank") }
            return
        }
        val removed = pages.removeAt(active)
        privateTabs.remove(removed)
        removed.stopLoading()
        removed.loadUrl("about:blank")
        removed.clearHistory()
        removed.clearCache(true)
        removed.destroy()
        active = (active - 1).coerceAtLeast(0)
        showOnlyActive(); updateTabs()
    }

    private fun togglePrivate() {
        privateMode = !privateMode
        Toast.makeText(this,
            if (privateMode) "Private tab opened. WebView cookies are shared with regular tabs; this is not fully private." else "Private mode off",
            Toast.LENGTH_LONG).show()
        newTab("about:blank")
    }

    private fun toggleDark() {
        dark = !dark
        root.setBackgroundColor(if (dark) Color.rgb(30,30,30) else Color.WHITE)
        address.setTextColor(if (dark) Color.WHITE else Color.BLACK)
        address.setHintTextColor(if (dark) Color.LTGRAY else Color.GRAY)
    }

    private fun bookmark() {
        val url = current()?.url ?: return
        if (!url.startsWith("https://") && !url.startsWith("http://")) return
        val list = JSONArray(prefs.getString("bookmarks", "[]"))
        if ((0 until list.length()).any { list.optString(it) == url }) {
            Toast.makeText(this, "Already bookmarked", Toast.LENGTH_SHORT).show(); return
        }
        list.put(url); prefs.edit().putString("bookmarks", list.toString()).apply()
        Toast.makeText(this, "Bookmark saved", Toast.LENGTH_SHORT).show()
    }

    private fun saveHistory(url: String) {
        val old = JSONArray(prefs.getString("history", "[]"))
        val updated = JSONArray()
        for (i in (old.length() - 299).coerceAtLeast(0) until old.length()) {
            val previous = old.optString(i)
            if (previous != url) updated.put(previous)
        }
        updated.put(url)
        prefs.edit().putString("history", updated.toString()).apply()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (current()?.canGoBack() == true) current()?.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        pages.forEach { it.stopLoading(); it.destroy() }
        pages.clear(); privateTabs.clear()
        super.onDestroy()
    }

    companion object {
        private val blockedHosts = setOf(
            "doubleclick.net", "googlesyndication.com", "adservice.google.com",
            "adnxs.com", "ads.yahoo.com", "scorecardresearch.com",
            "tracking.mdn.net", "googletagmanager.com"
        )
    }
}
