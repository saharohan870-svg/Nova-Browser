package com.zorvi.browser

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import org.json.JSONArray
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import java.net.URLEncoder
import java.util.Locale

/**
 * Nova Browser main shell backed by Mozilla GeckoView.
 * Each tab owns one GeckoSession; private tabs use Gecko's private-mode session setting.
 */
class MainActivity : Activity() {
    private data class Tab(val session: GeckoSession, val view: GeckoView, var url: String = "", var title: String = "", val private: Boolean = false)
    private lateinit var root: LinearLayout
    private lateinit var address: EditText
    private lateinit var tabsLabel: TextView
    private lateinit var engine: Spinner
    private val tabs = mutableListOf<Tab>()
    private var active = 0
    private var privateMode = false
    private var dark = false
    private val prefs by lazy { getSharedPreferences("zorvi", Context.MODE_PRIVATE) }
    private val engines = listOf("Google", "Bing", "DuckDuckGo", "Brave Search")
    private val runtime: GeckoRuntime by lazy { getOrCreateRuntime() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        engine = Spinner(this).apply {
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
                    navigate(text.toString()); true
                } else false
            }
        }
        top.addView(address, LinearLayout.LayoutParams(0, 48, 1f))
        top.addView(button("Go") { navigate(address.text.toString()) })
        root.addView(top)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(button("←") { current()?.session?.goBack() })
        controls.addView(button("→") { current()?.session?.goForward() })
        controls.addView(button("↻") { current()?.session?.reload() })
        controls.addView(button("★") { bookmark() })
        controls.addView(button("Bookmarks") { showSavedList(false) })
        controls.addView(button("History") { showSavedList(true) })
        tabsLabel = TextView(this).apply {
            text = "Tabs 0"; gravity = Gravity.CENTER; setPadding(7, 12, 7, 12)
            setOnClickListener { showTabs() }
        }
        controls.addView(tabsLabel)
        controls.addView(button("Zorvi") { openInZorvi() })
        controls.addView(button("Private") { togglePrivate() })
        controls.addView(button("☾") { dark = !dark; applyTheme() })
        root.addView(controls)
        setContentView(root)
        applyTheme()
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 11f; minWidth = 0
        setPadding(3, 0, 3, 0); setOnClickListener { action() }
    }

    private fun current(): Tab? = tabs.getOrNull(active)

    private fun newTab(url: String = "about:blank") {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(privateMode)
            .useTrackingProtection(true)
            .build()
        val session = GeckoSession(settings)
        val view = GeckoView(this)
        val tab = Tab(session, view, private = privateMode)
        session.setContentDelegate(object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title.orEmpty()
                if (current() === tab) updateTabs()
            }
            override fun onCloseRequest(session: GeckoSession) {
                if (current() === tab) closeTab()
            }
            override fun onCrash(session: GeckoSession) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Page process crashed. Reload this tab.", Toast.LENGTH_LONG).show() }
            }
        })
        session.setNavigationDelegate(object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                val next = url.orEmpty()
                tab.url = next
                if (current() === tab) address.setText(next)
                if (!tab.private && (next.startsWith("https://", true) || next.startsWith("http://", true))) saveHistory(next)
            }
        })
        session.open(runtime)
        view.setSession(session)
        tabs.add(tab)
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
            if (tab.view.parent == null) root.addView(tab.view, LinearLayout.LayoutParams(-1, 0, 1f))
            tab.session.setActive(index == active)
            tab.session.setFocused(index == active)
        }
        address.setText(current()?.url.orEmpty())
    }

    private fun navigate(input: String) {
        val value = input.trim()
        if (value.isEmpty()) return
        val uri = runCatching { Uri.parse(value) }.getOrNull()
        val scheme = uri?.scheme?.lowercase(Locale.ROOT)
        val hasScheme = scheme != null && scheme.matches(Regex("^[a-z][a-z0-9+.-]*$"))
        val target = when {
            hasScheme && (scheme == "http" || scheme == "https") -> value
            hasScheme -> {
                try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {
                    Toast.makeText(this, "Unsupported link type", Toast.LENGTH_SHORT).show()
                }
                return
            }
            value.contains(".") && !value.any { it.isWhitespace() } && !value.startsWith(".") -> "https://$value"
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
        current()?.session?.loadUri(target)
    }

    private fun openInZorvi() {
        val target = current()?.url?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        if (target.isNullOrBlank()) return
        startActivity(Intent(this, ZorviPageActivity::class.java).putExtra(ZorviPageActivity.EXTRA_URL, target))
    }

    private fun updateTabs() { tabsLabel.text = "Tabs ${tabs.size}" }

    private fun showTabs() {
        val names = tabs.mapIndexed { i, tab ->
            "${i + 1}. ${tab.title.ifBlank { tab.url.ifBlank { "New tab" } }}${if (tab.private) " [Private]" else ""}"
        }.toMutableList()
        names.add("＋ New tab")
        AlertDialog.Builder(this).setTitle("Nova tabs").setItems(names.toTypedArray()) { _, which ->
            if (which == tabs.size) newTab("https://www.google.com")
            else { active = which; showOnlyActive(); updateTabs() }
        }.setNegativeButton("Close current") { _, _ -> closeTab() }.show()
    }

    private fun closeTab() {
        val removed = current() ?: return
        tabs.removeAt(active)
        removed.session.setActive(false)
        removed.view.setSession(null)
        removed.session.close()
        if (tabs.isEmpty()) newTab("https://www.google.com")
        else { active = active.coerceAtMost(tabs.lastIndex); showOnlyActive(); updateTabs() }
    }

    private fun togglePrivate() {
        privateMode = !privateMode
        Toast.makeText(this, if (privateMode) "Private session enabled: Gecko private mode is active." else "Private mode off", Toast.LENGTH_SHORT).show()
        newTab("about:blank")
    }

    private fun applyTheme() {
        val bg = if (dark) Color.rgb(30, 30, 30) else Color.WHITE
        root.setBackgroundColor(bg)
        address.setTextColor(if (dark) Color.WHITE else Color.BLACK)
        address.setHintTextColor(if (dark) Color.LTGRAY else Color.GRAY)
    }

    private fun bookmark() {
        val url = current()?.url ?: return
        if (!url.startsWith("https://") && !url.startsWith("http://")) return
        val list = JSONArray(prefs.getString("bookmarks", "[]"))
        if ((0 until list.length()).any { list.optString(it) == url }) return
        list.put(url); prefs.edit().putString("bookmarks", list.toString()).apply()
        Toast.makeText(this, "Bookmark saved", Toast.LENGTH_SHORT).show()
    }

    private fun showSavedList(history: Boolean) {
        val key = if (history) "history" else "bookmarks"
        val list = JSONArray(prefs.getString(key, "[]"))
        val entries = (0 until list.length()).map { list.optString(it) }.reversed()
        if (entries.isEmpty()) {
            Toast.makeText(this, if (history) "No history yet" else "No bookmarks yet", Toast.LENGTH_SHORT).show(); return
        }
        AlertDialog.Builder(this).setTitle(if (history) "History" else "Bookmarks")
            .setItems(entries.toTypedArray()) { _, position -> current()?.session?.loadUri(entries[position]) }
            .setNeutralButton("Clear all") { _, _ -> prefs.edit().remove(key).apply() }
            .setNegativeButton("Close", null).show()
    }

    private fun saveHistory(url: String) {
        val old = JSONArray(prefs.getString("history", "[]"))
        val updated = JSONArray()
        for (i in (old.length() - 299).coerceAtLeast(0) until old.length()) {
            val previous = old.optString(i)
            if (previous != url) updated.put(previous)
        }
        updated.put(url); prefs.edit().putString("history", updated.toString()).apply()
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        val tab = current()
        if (tab != null) tab.session.goBack() else super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        current()?.session?.setActive(true)
    }

    override fun onPause() {
        current()?.session?.setActive(false)
        super.onPause()
    }

    override fun onDestroy() {
        tabs.forEach { tab ->
            tab.view.setSession(null)
            if (tab.session.isOpen) tab.session.close()
        }
        tabs.clear()
        super.onDestroy()
    }

    companion object {
        @Volatile private var sharedRuntime: GeckoRuntime? = null
        private fun getOrCreateRuntime(context: Context): GeckoRuntime =
            sharedRuntime ?: synchronized(this) {
                sharedRuntime ?: GeckoRuntime.create(context.applicationContext).also { sharedRuntime = it }
            }
    }

    private fun getOrCreateRuntime(): GeckoRuntime = getOrCreateRuntime(applicationContext)
}
