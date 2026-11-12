package com.zorvi.browser

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.app.AlertDialog
import android.database.Cursor
import org.json.JSONArray
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var address: EditText
    private lateinit var tabsLabel: TextView
    private val pages = mutableListOf<WebView>()
    private val privateTabs = mutableSetOf<WebView>()
    private var active = 0
    private var privateMode = false
    private var dark = false
    private var fileCallback: android.webkit.ValueCallback<Array<Uri>>? = null
    private val prefs by lazy { getSharedPreferences("zorvi", Context.MODE_PRIVATE) }
    private val engines = listOf("Google", "Bing", "DuckDuckGo", "Brave Search")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Clear leftovers from a prior session, including one terminated unexpectedly.
        clearPrivateProfileData()
        buildUi()
        newTab("https://www.google.com")
    }

    private fun buildUi() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(6, 4, 6, 4)
        }
        val engine = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, engines)
            setSelection(prefs.getInt("engine", 0).coerceIn(0, engines.lastIndex))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putInt("engine", position).apply()
                }
            }
        }
        top.addView(engine, LinearLayout.LayoutParams(104, 48))
        address = EditText(this).apply {
            isSingleLine = true
            hint = "Search or enter address"
            textSize = 14f
            imeOptions = EditorInfo.IME_ACTION_GO
            setSelectAllOnFocus(true)
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) {
                    navigate(text.toString(), engine.selectedItemPosition); true
                } else false
            }
        }
        top.addView(address, LinearLayout.LayoutParams(0, 48, 1f))
        top.addView(button("Go") { navigate(address.text.toString(), engine.selectedItemPosition) })
        root.addView(top)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(button("←") { if (current()?.canGoBack() == true) current()?.goBack() })
        controls.addView(button("→") { if (current()?.canGoForward() == true) current()?.goForward() })
        controls.addView(button("↻") { current()?.reload() })
        controls.addView(button("★") { bookmark() })
        controls.addView(button("Bookmarks") { showSavedList(false) })
        controls.addView(button("History") { showSavedList(true) })
        tabsLabel = TextView(this).apply {
            text = "Tabs 0"
            gravity = Gravity.CENTER
            setPadding(7, 12, 7, 12)
            setOnClickListener { showTabs() }
        }
        controls.addView(tabsLabel)
        controls.addView(button("Zorvi Engine") { openInZorvi() })
        controls.addView(button("Private") { togglePrivate() })
        controls.addView(button("☾") { toggleDark() })
        root.addView(controls)
        setContentView(root)
        applyTheme()
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 11f
        minWidth = 0
        setPadding(3, 0, 3, 0)
        setOnClickListener { action() }
    }

    private fun current(): WebView? = pages.getOrNull(active)

    private fun newTab(url: String = "about:blank") {
        val web = WebView(this)
        if (privateMode) {
            // Fail closed: never use the default profile for a private tab.
            check(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                "This Android System WebView does not support isolated profiles"
            }
            WebViewCompat.setProfile(web, PRIVATE_PROFILE_NAME)
        }
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (android.os.Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = true
            if (privateMode) cacheMode = WebSettings.LOAD_NO_CACHE
        }
        if (privateMode) privateTabs.add(web)
        val cookieManager = if (privateMode) {
            ProfileStore.getInstance().getOrCreateProfile(PRIVATE_PROFILE_NAME).cookieManager
        } else {
            CookieManager.getInstance()
        }
        cookieManager.setAcceptThirdPartyCookies(web, !privateMode)

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return true
                if (scheme == "http" || scheme == "https" || scheme == "about" || scheme == "blob" || scheme == "data") return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "No compatible app found for this link", Toast.LENGTH_SHORT).show()
                    true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (view === current()) address.setText(url.orEmpty())
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (view === current()) address.setText(url.orEmpty())
                if (view !in privateTabs && !url.isNullOrBlank() &&
                    (url.startsWith("https://", true) || url.startsWith("http://", true))) {
                    saveHistory(url)
                }
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val host = request?.url?.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return null
                if (blockedHosts.any { host == it || host.endsWith(".$it") }) {
                    return WebResourceResponse(
                        "text/plain", "utf-8", 204, "Blocked", emptyMap(),
                        ByteArrayInputStream(ByteArray(0))
                    )
                }
                return null
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                handler?.cancel()
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                callback: android.webkit.ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val intent = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                return try {
                    startActivityForResult(intent, FILE_PICKER_REQUEST)
                    true
                } catch (_: Exception) {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = null
                    false
                }
            }
        }

        web.setDownloadListener(DownloadListener { url, userAgent, disposition, mime, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                if (!mime.isNullOrBlank()) request.setMimeType(mime)
                request.addRequestHeader("User-Agent", userAgent.orEmpty())
                if (viewIsPrivate(web)) {
                    Toast.makeText(this, "Downloads are disabled in private tabs", Toast.LENGTH_LONG).show()
                } else {
                    CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
                    request.setTitle(URLUtil.guessFileName(url, disposition, mime))
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                    Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Unable to start download", Toast.LENGTH_SHORT).show()
            }
        })

        pages.add(web)
        active = pages.lastIndex
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        showOnlyActive()
        updateTabs()
        if (url != "about:blank") web.loadUrl(url)
    }

    private fun viewIsPrivate(web: WebView) = web in privateTabs

    private fun showOnlyActive() {
        if (root.childCount > 2) root.removeViews(2, root.childCount - 2)
        pages.forEachIndexed { index, web ->
            web.visibility = if (index == active) View.VISIBLE else View.GONE
            root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        address.setText(current()?.url.orEmpty())
    }

    private fun navigate(input: String, engineIndex: Int = prefs.getInt("engine", 0)) {
        val value = input.trim()
        if (value.isEmpty()) return
        val uri = runCatching { Uri.parse(value) }.getOrNull()
        val scheme = uri?.scheme?.lowercase(Locale.ROOT)
        val hasScheme = scheme != null && scheme.matches(Regex("^[a-z][a-z0-9+.-]*$"))
        val normalized = if (hasScheme && (scheme == "http" || scheme == "https")) {
            scheme + value.substringAfter(':')
        } else value
        val hostCandidate = value.contains(".") && !value.any { it.isWhitespace() } &&
            !value.startsWith(".") && !value.startsWith("www..")
        val target = when {
            hasScheme && (scheme == "http" || scheme == "https") -> normalized
            hasScheme -> {
                Toast.makeText(this, "Only web addresses and searches are supported", Toast.LENGTH_SHORT).show()
                return
            }
            hostCandidate -> "https://$value"
            else -> {
                val q = URLEncoder.encode(value, "UTF-8")
                when (engineIndex.coerceIn(0, engines.lastIndex)) {
                    1 -> "https://www.bing.com/search?q=$q"
                    2 -> "https://duckduckgo.com/?q=$q"
                    3 -> "https://search.brave.com/search?q=$q"
                    else -> "https://www.google.com/search?q=$q"
                }
            }
        }
        current()?.loadUrl(target)
    }

    private fun openInZorvi() {
        val target = current()?.url?.takeIf {
            it.startsWith("https://", true) || it.startsWith("http://", true)
        } ?: address.text.toString().trim().takeIf { it.isNotEmpty() }
        if (target.isNullOrBlank()) {
            Toast.makeText(this, "Enter a web address first", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, ZorviPageActivity::class.java).putExtra(ZorviPageActivity.EXTRA_URL, target))
    }

    private fun updateTabs() { tabsLabel.text = "Tabs ${pages.size}" }

    private fun showTabs() {
        val names = pages.mapIndexed { i, web ->
            val mode = if (web in privateTabs) " [Private]" else ""
            "${i + 1}. ${web.title ?: web.url ?: "New tab"}$mode"
        }.toMutableList()
        names.add("＋ New tab")
        AlertDialog.Builder(this)
            .setTitle("Zorvi tabs")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == pages.size) newTab("https://www.google.com")
                else { active = which; showOnlyActive(); updateTabs() }
            }
            .setNegativeButton("Close current") { _, _ -> closeTab() }
            .show()
    }

    private fun closeTab() {
        val removed = current() ?: return
        if (pages.size == 1) {
            removed.stopLoading()
            if (removed in privateTabs) clearPrivateWebView(removed)
            removed.loadUrl("about:blank")
            address.setText("")
            return
        }
        pages.removeAt(active)
        val wasPrivate = removed in privateTabs
        if (wasPrivate) privateTabs.remove(removed)
        removed.stopLoading()
        removed.loadUrl("about:blank")
        removed.clearHistory()
        removed.clearCache(true)
        removed.destroy()
        if (wasPrivate && privateTabs.isEmpty()) clearPrivateProfileData()
        active = active.coerceAtMost(pages.lastIndex)
        showOnlyActive()
        updateTabs()
    }

    private fun clearPrivateProfileData() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        runCatching {
            val profile = ProfileStore.getInstance().getOrCreateProfile(PRIVATE_PROFILE_NAME)
            profile.cookieManager.removeAllCookies(null)
            profile.cookieManager.flush()
            profile.webStorage.deleteAllData()
        }
    }

    private fun togglePrivate() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            Toast.makeText(this, "Private browsing is unavailable: update Android System WebView to a version with multi-profile support.", Toast.LENGTH_LONG).show()
            return
        }
        privateMode = !privateMode
        Toast.makeText(this, if (privateMode) "Isolated private profile enabled. Data is cleared when its last tab closes." else "Private mode off", Toast.LENGTH_SHORT).show()
        newTab("about:blank")
    }

    private fun toggleDark() {
        dark = !dark
        applyTheme()
    }

    private fun applyTheme() {
        val background = if (dark) Color.rgb(30, 30, 30) else Color.WHITE
        root.setBackgroundColor(background)
        address.setTextColor(if (dark) Color.WHITE else Color.BLACK)
        address.setHintTextColor(if (dark) Color.LTGRAY else Color.GRAY)
    }

    private fun bookmark() {
        val url = current()?.url ?: return
        if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) return
        val list = JSONArray(prefs.getString("bookmarks", "[]"))
        if ((0 until list.length()).any { list.optString(it) == url }) {
            Toast.makeText(this, "Already bookmarked", Toast.LENGTH_SHORT).show()
            return
        }
        list.put(url)
        prefs.edit().putString("bookmarks", list.toString()).apply()
        Toast.makeText(this, "Bookmark saved", Toast.LENGTH_SHORT).show()
    }

    private fun showSavedList(history: Boolean) {
        val key = if (history) "history" else "bookmarks"
        val list = JSONArray(prefs.getString(key, "[]"))
        val entries = (0 until list.length()).map { list.optString(it) }.reversed()
        if (entries.isEmpty()) {
            Toast.makeText(this, if (history) "No history yet" else "No bookmarks yet", Toast.LENGTH_SHORT).show()
            return
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, entries)
        val dialog = AlertDialog.Builder(this).setTitle(if (history) "History" else "Bookmarks")
            .setAdapter(adapter) { _, position -> current()?.loadUrl(entries[position]) }
            .setNeutralButton("Clear all") { _, _ ->
                prefs.edit().remove(key).apply()
                Toast.makeText(this, if (history) "History cleared" else "Bookmarks cleared", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Close", null).create()
        dialog.show()
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

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_PICKER_REQUEST) {
            val result = if (resultCode == RESULT_OK && data != null) {
                val clip = data.clipData
                if (clip != null) Array(clip.itemCount) { index -> clip.getItemAt(index).uri }
                else data.data?.let { arrayOf(it) }
            } else null
            fileCallback?.onReceiveValue(result)
            fileCallback = null
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (current()?.canGoBack() == true) current()?.goBack()
        else if (pages.size > 1) closeTab()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        pages.forEach { it.stopLoading(); it.destroy() }
        pages.clear()
        privateTabs.clear()
        clearPrivateProfileData()
        super.onDestroy()
    }

    companion object {
        private const val FILE_PICKER_REQUEST = 4102
        private const val PRIVATE_PROFILE_NAME = "zorvi_ephemeral_private"
        private val blockedHosts = setOf(
            "doubleclick.net", "googlesyndication.com", "adservice.google.com",
            "adnxs.com", "ads.yahoo.com", "scorecardresearch.com",
            "tracking.mdn.net", "googletagmanager.com"
        )
    }
}
