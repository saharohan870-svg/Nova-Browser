package com.zorvi.browser

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/** Main browser surface. Web pages are rendered by GeckoView, never Android WebView. */
class MainActivity : Activity() {
    private data class BrowserTab(val session: GeckoSession, val view: GeckoView, val isPrivate: Boolean, var title: String = "New tab", var group: String = "Default", var canGoBack: Boolean = false)

    private lateinit var root: LinearLayout
    private lateinit var address: EditText
    private lateinit var tabsLabel: TextView
    private lateinit var runtime: GeckoRuntime
    private val tabs = mutableListOf<BrowserTab>()
    private var active = 0
    private var privateMode = false
    private var dark = false
    private val prefs by lazy { getSharedPreferences("zorvi", Context.MODE_PRIVATE) }
    private val engines = listOf("Google", "Bing", "DuckDuckGo", "Brave Search")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = GeckoRuntime.create(this)
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
        val searchEngine = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, engines)
            setSelection(prefs.getInt("engine", 0).coerceIn(0, engines.lastIndex))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putInt("engine", position).apply()
                }
            }
        }
        top.addView(searchEngine, LinearLayout.LayoutParams(104, 48))
        address = EditText(this).apply {
            isSingleLine = true
            hint = "Search or enter address"
            textSize = 14f
            imeOptions = EditorInfo.IME_ACTION_GO
            setSelectAllOnFocus(true)
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) {
                    navigate(text.toString(), searchEngine.selectedItemPosition)
                    true
                } else false
            }
        }
        top.addView(address, LinearLayout.LayoutParams(0, 48, 1f))
        top.addView(button("Go") { navigate(address.text.toString(), searchEngine.selectedItemPosition) })
        root.addView(top)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(button("←") { activeSession()?.goBack() })
        controls.addView(button("→") { activeSession()?.goForward() })
        controls.addView(button("↻") { activeSession()?.reload() })
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
        controls.addView(button("Group") { assignTabGroup() })
        controls.addView(button("VPN") { openVpnSettings() })
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

    private fun activeTab() = tabs.getOrNull(active)
    private fun activeSession() = activeTab()?.session

    private fun newTab(url: String = "about:blank") {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(privateMode)
            .useTrackingProtection(true)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .userAgentOverride(CHROME_ANDROID_UA)
            .build()
        val session = GeckoSession(settings)
        val view = GeckoView(this)
        session.open(runtime)
        view.setSession(session)
        session.setNavigationDelegate(object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                if (session === activeSession()) address.setText(url.orEmpty())
                val tab = tabs.firstOrNull { it.session === session }
                if (tab?.isPrivate == false && !url.isNullOrBlank() &&
                    (url.startsWith("https://") || url.startsWith("http://"))) {
                    saveHistory(url)
                }
            }
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tabs.firstOrNull { it.session === session }?.canGoBack = canGoBack
            }
        })
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                val tab = tabs.firstOrNull { it.session === session } ?: return
                tab.title = title?.takeIf { it.isNotBlank() } ?: "New tab"
                if (session === activeSession()) updateTabs()
            }
        }
        tabs.add(BrowserTab(session, view, privateMode, group = activeTab()?.group ?: "Default"))
        active = tabs.lastIndex
        root.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
        showOnlyActive()
        updateTabs()
        if (url != "about:blank") session.loadUri(url)
    }

    private fun showOnlyActive() {
        if (root.childCount > 2) root.removeViews(2, root.childCount - 2)
        tabs.forEachIndexed { index, tab ->
            tab.view.visibility = if (index == active) View.VISIBLE else View.GONE
            root.addView(tab.view, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    private fun navigate(input: String, engineIndex: Int = prefs.getInt("engine", 0)) {
        val value = input.trim()
        if (value.isEmpty()) return
        val parsed = runCatching { URI(value) }.getOrNull()
        val scheme = parsed?.scheme?.lowercase(Locale.ROOT)
        val target = when {
            scheme == "http" || scheme == "https" -> value
            scheme != null -> {
                Toast.makeText(this, "Only HTTP and HTTPS pages are supported", Toast.LENGTH_SHORT).show()
                return
            }
            value.contains(".") && !value.any { it.isWhitespace() } -> "https://" + value
            else -> {
                val q = URLEncoder.encode(value, "UTF-8")
                when (engineIndex.coerceIn(0, engines.lastIndex)) {
                    1 -> "https://www.bing.com/search?q=" + q
                    2 -> "https://duckduckgo.com/?q=" + q
                    3 -> "https://search.brave.com/search?q=" + q
                    else -> "https://www.google.com/search?q=" + q
                }
            }
        }
        address.setText(target)
        activeSession()?.loadUri(target)
    }

    private fun updateTabs() { tabsLabel.text = "Tabs " + tabs.size }

    private fun showTabs() {
        val labels = tabs.mapIndexed { index, tab ->
            (index + 1).toString() + ". " + (if (tab.isPrivate) "[Private] " else "") +
                tab.title.take(48)
        }.toMutableList()
        labels.add("＋ New tab")
        AlertDialog.Builder(this).setTitle("Zorvi tabs")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == tabs.size) newTab("https://www.google.com")
                else { active = which; showOnlyActive(); updateTabs() }
            }
            .setNegativeButton("Close current") { _, _ -> closeTab() }
            .show()
    }

    private fun assignTabGroup() {
        val tab = activeTab() ?: return
        val existing = tabs.map { it.group }.distinct().toMutableList()
        existing.add("＋ Create new group")
        AlertDialog.Builder(this)
            .setTitle("Move tab to group")
            .setItems(existing.toTypedArray()) { _, which ->
                if (which == existing.lastIndex) {
                    val nameInput = EditText(this).apply {
                        hint = "Group name"
                        setSingleLine(true)
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Create tab group")
                        .setView(nameInput)
                        .setPositiveButton("Create") { _, _ ->
                            val name = nameInput.text.toString().trim().take(24)
                            if (name.isNotEmpty()) {
                                tab.group = name
                                updateTabs()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    tab.group = existing[which]
                    updateTabs()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun closeTab() {
        if (tabs.isEmpty()) return
        if (tabs.size == 1) {
            activeSession()?.loadUri("about:blank")
            address.setText("")
            return
        }
        val removed = tabs.removeAt(active)
        removed.session.close()
        removed.view.releaseSession()
        active = active.coerceAtMost(tabs.lastIndex)
        showOnlyActive()
        updateTabs()
    }

    private fun openVpnSettings() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_VPN_SETTINGS)
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "Open Android Settings → Network & internet → VPN, then connect Turbo VPN", Toast.LENGTH_LONG).show()
        }
    }

    private fun togglePrivate() {
        privateMode = !privateMode
        Toast.makeText(
            this,
            if (privateMode) "New tabs use GeckoView private sessions" else "Private mode off",
            Toast.LENGTH_SHORT
        ).show()
        newTab("about:blank")
    }

    private fun toggleDark() {
        dark = !dark
        applyTheme()
    }

    private fun applyTheme() {
        root.setBackgroundColor(if (dark) Color.rgb(30, 30, 30) else Color.WHITE)
        address.setTextColor(if (dark) Color.WHITE else Color.BLACK)
        address.setHintTextColor(if (dark) Color.LTGRAY else Color.GRAY)
    }

    private fun bookmark() {
        val url = address.text.toString()
        if (!url.startsWith("https://") && !url.startsWith("http://")) return
        val list = org.json.JSONArray(prefs.getString("bookmarks", "[]"))
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
        val list = org.json.JSONArray(prefs.getString(key, "[]"))
        val entries = (0 until list.length()).map { list.optString(it) }.reversed()
        if (entries.isEmpty()) {
            Toast.makeText(this, if (history) "No history yet" else "No bookmarks yet", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this).setTitle(if (history) "History" else "Bookmarks")
            .setItems(entries.toTypedArray()) { _, position ->
                address.setText(entries[position])
                activeSession()?.loadUri(entries[position])
            }
            .setNeutralButton("Clear all") { _, _ -> prefs.edit().remove(key).apply() }
            .setNegativeButton("Close", null).show()
    }

    private fun saveHistory(url: String) {
        val old = org.json.JSONArray(prefs.getString("history", "[]"))
        val updated = org.json.JSONArray()
        for (i in (old.length() - 299).coerceAtLeast(0) until old.length()) {
            val previous = old.optString(i)
            if (previous != url) updated.put(previous)
        }
        updated.put(url)
        prefs.edit().putString("history", updated.toString()).apply()
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        val session = activeSession()
        if (session != null && activeTab()?.canGoBack == true) session.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        tabs.forEach { tab ->
            tab.session.close()
            tab.view.releaseSession()
        }
        tabs.clear()
        super.onDestroy()
    }

    companion object {
        // Browser-identification compatibility only; Gecko remains the rendering engine.
        private const val CHROME_ANDROID_UA =
            "Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36"
    }
}
