package com.nova.browser

import android.app.*
import android.os.Bundle
import android.content.*
import android.graphics.Color
import android.view.*
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import org.json.JSONArray
import java.net.URLEncoder

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var address: EditText
    private lateinit var engine: Spinner
    private lateinit var tabsLabel: TextView
    private val pages = mutableListOf<WebView>()
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
            setSelection(prefs.getInt("engine", 0))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { prefs.edit().putInt("engine", position).apply() }
            }
        }
        top.addView(engine, LinearLayout.LayoutParams(112, 48))
        address = EditText(this).apply {
            singleLine = true; hint = "Search or enter address"; textSize = 14f
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_GO) { navigate(text.toString()); true } else false }
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
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.loadsImagesAutomatically = true
        web.settings.setSupportMultipleWindows(false)
        if (privateMode) {
            web.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            web.settings.saveFormData = false
            CookieManager.getInstance().setAcceptCookie(false)
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url?.toString() ?: return false
                if (u.startsWith("http://") || u.startsWith("https://")) return false
                return true
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) { if (view === current()) address.setText(url ?: "") }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (view === current()) address.setText(url ?: "")
                if (!privateMode && url?.startsWith("http") == true) saveHistory(url)
            }
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                val host = request?.url?.host?.lowercase() ?: return null
                if (host in blockedHosts) return WebResourceResponse("text/plain", "utf-8", 204, "Blocked", mapOf(), java.io.ByteArrayInputStream(ByteArray(0)))
                return null
            }
        }
        web.setDownloadListener { url, _, disposition, mime, _ ->
            val req = DownloadManager.Request(android.net.Uri.parse(url))
            req.setMimeType(mime); req.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
            req.setTitle(URLUtil.guessFileName(url, disposition, mime)); req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show()
        }
        pages.add(web); active = pages.lastIndex
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        showOnlyActive()
        updateTabs()
        if (url != "about:blank") web.loadUrl(url)
    }

    private fun showOnlyActive() {
        pages.forEachIndexed { i, w -> w.visibility = if (i == active) View.VISIBLE else View.GONE }
        root.removeViews(2, root.childCount - 2)
        pages.forEach { w -> root.addView(w, LinearLayout.LayoutParams(-1, 0, 1f)) }
    }

    private fun navigate(input: String) {
        val value = input.trim()
        if (value.isEmpty()) return
        val url = if (value.matches(Regex("(?i)^(https?://).+")) || value.contains(".") && !value.contains(" ")) {
            if (value.startsWith("http")) value else "https://$value"
        } else {
            val q = URLEncoder.encode(value, "UTF-8")
            when (engine.selectedItemPosition) {
                1 -> "https://www.bing.com/search?q=$q"
                2 -> "https://duckduckgo.com/?q=$q"
                3 -> "https://search.brave.com/search?q=$q"
                else -> "https://www.google.com/search?q=$q"
            }
        }
        current()?.loadUrl(url)
    }

    private fun updateTabs() { tabsLabel.text = "Tabs ${pages.size}" }

    private fun showTabs() {
        val names = pages.mapIndexed { i, w -> "${i + 1}. ${w.title ?: w.url ?: "New tab"}" }.toMutableList()
        names.add("＋ New tab")
        AlertDialog.Builder(this).setTitle("Nova tabs").setItems(names.toTypedArray()) { _, which ->
            if (which == pages.size) newTab("https://www.google.com") else { active = which; showOnlyActive(); updateTabs(); address.setText(current()?.url ?: "") }
        }.setNegativeButton("Close current") { _, _ -> closeTab() }.show()
    }

    private fun closeTab() {
        if (pages.size <= 1) { current()?.loadUrl("about:blank"); return }
        pages.removeAt(active).apply { stopLoading(); destroy() }
        active = (active - 1).coerceAtLeast(0)
        showOnlyActive(); updateTabs()
    }

    private fun togglePrivate() {
        privateMode = !privateMode
        if (privateMode) {
            CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush()
            Toast.makeText(this, "Private tab mode on for new tabs", Toast.LENGTH_SHORT).show()
            newTab("about:blank")
        } else {
            CookieManager.getInstance().setAcceptCookie(true)
            Toast.makeText(this, "Private mode off", Toast.LENGTH_SHORT).show()
            newTab("about:blank")
        }
    }

    private fun toggleDark() {
        dark = !dark
        root.setBackgroundColor(if (dark) Color.rgb(30,30,30) else Color.WHITE)
        address.setTextColor(if (dark) Color.WHITE else Color.BLACK)
        address.setHintTextColor(if (dark) Color.LTGRAY else Color.GRAY)
    }

    private fun bookmark() {
        val url = current()?.url ?: return
        val list = JSONArray(prefs.getString("bookmarks", "[]"))
        if ((0 until list.length()).any { list.optString(it) == url }) {
            Toast.makeText(this, "Already bookmarked", Toast.LENGTH_SHORT).show(); return
        }
        list.put(url); prefs.edit().putString("bookmarks", list.toString()).apply()
        Toast.makeText(this, "Bookmark saved", Toast.LENGTH_SHORT).show()
    }

    private fun saveHistory(url: String) {
        val list = JSONArray(prefs.getString("history", "[]"))
        if (list.length() > 300) { val trimmed = JSONArray(); for (i in 100 until list.length()) trimmed.put(list.get(i)); prefs.edit().putString("history", trimmed.toString()).apply() }
        val updated = JSONArray(prefs.getString("history", "[]")); updated.put(url)
        prefs.edit().putString("history", updated.toString()).apply()
    }

    override fun onBackPressed() { if (current()?.canGoBack() == true) current()?.goBack() else super.onBackPressed() }

    override fun onDestroy() {
        pages.forEach { it.stopLoading(); it.destroy() }
        pages.clear()
        super.onDestroy()
    }

    companion object {
        private val blockedHosts = setOf("doubleclick.net", "googlesyndication.com", "adservice.google.com", "adnxs.com", "ads.yahoo.com", "scorecardresearch.com", "tracking.mdn.net")
    }
}
