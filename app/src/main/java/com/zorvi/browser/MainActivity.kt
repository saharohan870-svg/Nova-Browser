package com.zorvi.browser

import android.app.Activity
import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.app.AlertDialog
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Build
import android.util.Rational
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import org.json.JSONArray
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoResult
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
    private data class Tab(val session: GeckoSession, val view: GeckoView, var url: String = "", var title: String = "", val private: Boolean = false, var group: String = "General", var serializedState: String? = null, var canGoBack: Boolean = false, var canGoForward: Boolean = false, var pinned: Boolean = false)
    private lateinit var root: LinearLayout
    private lateinit var address: EditText
    private lateinit var tabsLabel: TextView
    private lateinit var engine: Spinner
    private val tabs = mutableListOf<Tab>()
    private var active = 0
    private var privateMode = false
    private var dark = false
    private var desktopMode = false
    private var fullScreen = false
    private var pendingFilePrompt: GeckoSession.PromptDelegate.FilePrompt? = null
    private var pendingFileResult: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = null
    private var pendingAndroidPermission: GeckoSession.PermissionDelegate.Callback? = null
    private val requestFile = 4101
    private val requestWebPermission = 4102
    private val prefs by lazy { getSharedPreferences("zorvi", Context.MODE_PRIVATE) }
    private val engines = listOf("Google", "Bing", "DuckDuckGo", "Brave Search")
    private val runtime: GeckoRuntime by lazy { getOrCreateRuntime() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        restoreTabs()
        if (tabs.isEmpty()) newTab(homePage())
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
        controls.addView(button("＋") { newTab("https://www.google.com") })
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
        controls.addView(button("AI Sites") { showCompatibilitySites(true) })
        controls.addView(button("Sites") { showCompatibilitySites(false) })
        controls.addView(button("Desktop") { toggleDesktopMode() })
        controls.addView(button("Home") { openHome() })
        controls.addView(button("Tools") { showAdvancedTools() })
        controls.addView(button("Shield") { showPrivacyControls() })
        controls.addView(button("Group") { manageGroups() })
        controls.addView(button("Downloads") { showDownloads() })
        controls.addView(button("PiP") { enterPip() })
        controls.addView(button("Zorvi") { openInZorvi() })
        controls.addView(button("Private") { togglePrivate() })
        controls.addView(button("☾") { dark = !dark; applyTheme() })
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(controls)
        })
        setContentView(root)
        applyTheme()
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 11f; minWidth = 0
        setPadding(3, 0, 3, 0); setOnClickListener { action() }
    }

    private fun current(): Tab? = tabs.getOrNull(active)

    private fun homePage(): String = prefs.getString("home_page", "https://www.google.com") ?: "https://www.google.com"

    private fun openHome() {
        current()?.session?.loadUri(homePage()) ?: newTab(homePage())
    }

    private fun customizeHomePage() {
        val field = EditText(this).apply { hint = "https://example.com"; setText(homePage()); isSingleLine = true }
        AlertDialog.Builder(this).setTitle("Set home page")
            .setMessage("Enter the page Nova should open with Home and new tabs.")
            .setView(field).setPositiveButton("Save") { _, _ ->
                val raw = field.text.toString().trim()
                val value = if (raw.startsWith("https://") || raw.startsWith("http://")) raw else "https://$raw"
                if (Uri.parse(value).host != null) {
                    prefs.edit().putString("home_page", value).apply()
                    Toast.makeText(this, "Home page saved", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this, "Enter a valid website address", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showAdvancedTools() {
        val actions = arrayOf("Ask AI assistant", "Summarize current page with AI", "Translate current page", "Translation language", "Reader view", "Customize home page")
        AlertDialog.Builder(this).setTitle("Nova tools").setItems(actions) { _, which ->
            when (which) {
                0 -> askAiAssistant()
                1 -> summarizeCurrentPage()
                2 -> translateCurrentPage()
                3 -> chooseTranslationLanguage()
                4 -> openReaderView()
                5 -> customizeHomePage()
            }
        }.show()
    }

    private fun chooseAiProvider(prompt: String, title: String) {
        val providers = arrayOf("Perplexity", "ChatGPT")
        AlertDialog.Builder(this).setTitle(title).setItems(providers) { _, which ->
            val encoded = URLEncoder.encode(prompt, "UTF-8")
            val target = if (which == 0) "https://www.perplexity.ai/search?q=$encoded"
                else "https://chatgpt.com/?q=$encoded"
            newTab(target)
        }.setNegativeButton("Cancel", null).show()
    }

    private fun askAiAssistant() {
        val prompt = EditText(this).apply { hint = "Ask anything…" }
        AlertDialog.Builder(this).setTitle("Nova AI assistant")
            .setMessage("Choose Perplexity or ChatGPT after entering your question.")
            .setView(prompt).setPositiveButton("Continue") { _, _ ->
                val question = prompt.text.toString().trim()
                if (question.isNotEmpty()) chooseAiProvider(question, "Choose AI assistant")
            }.setNegativeButton("Cancel", null).show()
    }

    private fun summarizeCurrentPage() {
        val url = current()?.url.orEmpty()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(this, "Open a webpage first", Toast.LENGTH_SHORT).show(); return
        }
        val prompt = "Please open and summarize this webpage. If you cannot access it, tell me: $url"
        chooseAiProvider(prompt, "Summarize with")
    }

    private fun translateCurrentPage() {
        val url = current()?.url.orEmpty()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(this, "Open a webpage first", Toast.LENGTH_SHORT).show(); return
        }
        val language = prefs.getString("translation_language", "en") ?: "en"
        current()?.session?.loadUri("https://translate.google.com/translate?sl=auto&tl=" + language + "&u=" + URLEncoder.encode(url, "UTF-8"))
    }

    private fun chooseTranslationLanguage() {
        val codes = arrayOf("en", "hi", "as", "bn", "fr", "de", "es", "ja", "zh-CN")
        val labels = arrayOf("English", "Hindi", "Assamese", "Bengali", "French", "German", "Spanish", "Japanese", "Chinese")
        val selected = codes.indexOf(prefs.getString("translation_language", "en")).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("Translate pages into")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                prefs.edit().putString("translation_language", codes[which]).apply()
                dialog.dismiss()
                Toast.makeText(this, "Translation language: " + labels[which], Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun openReaderView() {
        val url = current()?.url.orEmpty()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(this, "Open an article first", Toast.LENGTH_SHORT).show(); return
        }
        current()?.session?.loadUri("about:reader?url=" + URLEncoder.encode(url, "UTF-8"))
    }

    private fun showPrivacyControls() {
        val enabled = prefs.getBoolean("tracking_protection", true)
        val message = "GeckoView tracking protection is " + (if (enabled) "enabled." else "disabled.") +
            "\\n\\nThis applies to newly created tabs. It is not a complete standalone ad blocker."
        AlertDialog.Builder(this).setTitle("Privacy and protection").setMessage(message)
            .setPositiveButton(if (enabled) "Turn off" else "Turn on") { _, _ ->
                prefs.edit().putBoolean("tracking_protection", !enabled).apply()
                Toast.makeText(this, "Protection setting saved. It applies to new tabs.", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("Clear browsing data") { _, _ ->
                AlertDialog.Builder(this).setTitle("Clear local browser data")
                    .setMessage("Clear saved history, bookmarks and tab restore data? Website cookies are not cleared.")
                    .setPositiveButton("Clear") { _, _ ->
                        prefs.edit().remove("history").remove("bookmarks").remove("open_tabs").apply()
                        Toast.makeText(this, "Local history, bookmarks and saved tabs cleared", Toast.LENGTH_LONG).show()
                    }.setNegativeButton("Cancel", null).show()
            }.setNegativeButton("Close", null).show()
    }

    private fun newTab(url: String = homePage(), restoredState: String? = null) {
        desktopMode = prefs.getBoolean("desktop_mode", false)
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(privateMode)
            .useTrackingProtection(prefs.getBoolean("tracking_protection", true))
            .allowJavascript(true)
            .userAgentMode(if (desktopMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(if (desktopMode) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build()
        val session = GeckoSession(settings)
        val view = GeckoView(this)
        val tab = Tab(session, view, private = privateMode, group = if (privateMode) "Private" else (prefs.getString("active_group", "General") ?: "General"))
        session.setContentDelegate(object : GeckoSession.ContentDelegate {
            override fun onExternalResponse(session: GeckoSession, response: org.mozilla.geckoview.WebResponse) {
                runOnUiThread { queueDownload(response.uri, response.headers["content-disposition"], response.headers["content-type"], response.body) }
            }
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                runOnUiThread { setBrowserFullScreen(fullScreen) }
            }
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title.orEmpty()
                if (current() === tab) updateTabs()
            }
            override fun onCloseRequest(session: GeckoSession) {
                if (current() === tab) closeTab()
            }
            override fun onCrash(session: GeckoSession) {
                runOnUiThread {
                    AlertDialog.Builder(this@MainActivity).setTitle("Page crashed")
                        .setMessage("This tab stopped unexpectedly.")
                        .setPositiveButton("Reload") { _, _ -> tab.session.reload() }
                        .setNegativeButton("Close tab") { _, _ -> if (current() === tab) closeTab() }
                        .setNeutralButton("Keep tab", null).show()
                }
            }
        })
        session.setNavigationDelegate(object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) { tab.canGoBack = canGoBack }
            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) { tab.canGoForward = canGoForward }
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                newTab("about:blank")
                return GeckoResult.fromValue(current()!!.session)
            }
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                val next = url.orEmpty()
                tab.url = next
                if (current() === tab) address.setText(next)
                if (!tab.private && (next.startsWith("https://", true) || next.startsWith("http://", true))) {
                    saveHistory(next)
                    saveTabs()
                }
            }
        })
        session.setPromptDelegate(object : GeckoSession.PromptDelegate {
            override fun onFilePrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.FilePrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                pendingFilePrompt = prompt
                pendingFileResult = result
                val intent = if (prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.FOLDER) {
                    Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                } else {
                    Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
                        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE)
                }
                startActivityForResult(intent, requestFile)
                return result
            }
        })
        session.setPermissionDelegate(object : GeckoSession.PermissionDelegate {
            override fun onAndroidPermissionsRequest(session: GeckoSession, permissions: Array<String>?, callback: GeckoSession.PermissionDelegate.Callback) {
                if (permissions.isNullOrEmpty()) { callback.reject(); return }
                pendingAndroidPermission = callback
                requestPermissions(permissions, requestWebPermission)
            }
            override fun onContentPermissionRequest(session: GeckoSession, perm: GeckoSession.PermissionDelegate.ContentPermission): GeckoResult<Int> {
                val result = GeckoResult<Int>()
                val host = runCatching { Uri.parse(perm.uri).host }.getOrNull() ?: "this website"
                val permissionKey = "site_permission_" + host + "_" + perm.permission
                val remembered = prefs.getInt(permissionKey, -1)
                if (remembered == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW || remembered == GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY) {
                    result.complete(remembered)
                    return result
                }
                runOnUiThread {
                    val remember = CheckBox(this@MainActivity).apply { text = "Remember this choice for " + host }
                    val content = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(32, 8, 32, 0)
                        addView(TextView(this@MainActivity).apply { text = "Allow " + host + " to access this website permission?" })
                        addView(remember)
                    }
                    val dialog = AlertDialog.Builder(this@MainActivity).setTitle("Website permission")
                        .setView(content)
                        .setPositiveButton("Allow") { _, _ ->
                            val value = GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                            if (remember.isChecked) prefs.edit().putInt(permissionKey, value).apply()
                            result.complete(value)
                        }
                        .setNegativeButton("Block") { _, _ ->
                            val value = GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                            if (remember.isChecked) prefs.edit().putInt(permissionKey, value).apply()
                            result.complete(value)
                        }
                        .setOnCancelListener { result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY) }.create()
                    dialog.setOnShowListener { perm.notifyShown() }
                    dialog.show()
                }
                return result
            }
            override fun onMediaPermissionRequest(session: GeckoSession, uri: String, video: Array<GeckoSession.PermissionDelegate.MediaSource>?, audio: Array<GeckoSession.PermissionDelegate.MediaSource>?, callback: GeckoSession.PermissionDelegate.MediaCallback) {
                runOnUiThread {
                    AlertDialog.Builder(this@MainActivity).setTitle("Camera / microphone")
                        .setMessage("Allow this website to use the requested camera or microphone?")
                        .setPositiveButton("Allow") { _, _ -> callback.grant(video?.firstOrNull(), audio?.firstOrNull()) }
                        .setNegativeButton("Block") { _, _ -> callback.reject() }.show()
                }
            }
        })
        session.setProgressDelegate(object : GeckoSession.ProgressDelegate {
            override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
                tab.serializedState = sessionState.toString()
                if (!tab.private) saveTabs()
            }
        })
        session.open(runtime)
        if (!restoredState.isNullOrBlank()) {
            runCatching { GeckoSession.SessionState.fromString(restoredState) }.getOrNull()?.let { session.restoreState(it) }
        } else if (url != "about:blank") {
            session.loadUri(url)
        }
        view.setSession(session)
        tabs.add(tab)
        active = tabs.lastIndex
        saveTabs()
        root.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
        showOnlyActive()
        updateTabs()
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

    private data class SiteShortcut(val name: String, val url: String)

    private val aiShortcuts = listOf(
        SiteShortcut("ChatGPT", "https://chatgpt.com/"),
        SiteShortcut("Claude", "https://claude.ai/"),
        SiteShortcut("Google Gemini", "https://gemini.google.com/"),
        SiteShortcut("Microsoft Copilot", "https://copilot.microsoft.com/"),
        SiteShortcut("Perplexity", "https://www.perplexity.ai/"),
        SiteShortcut("Grok", "https://grok.com/"),
        SiteShortcut("DeepSeek", "https://chat.deepseek.com/"),
        SiteShortcut("Mistral Le Chat", "https://chat.mistral.ai/"),
        SiteShortcut("Meta AI", "https://www.meta.ai/")
    )

    private val otherShortcuts = listOf(
        SiteShortcut("Google", "https://www.google.com/"),
        SiteShortcut("YouTube", "https://www.youtube.com/"),
        SiteShortcut("GitHub", "https://github.com/"),
        SiteShortcut("Instagram", "https://www.instagram.com/"),
        SiteShortcut("Discord", "https://discord.com/app"),
        SiteShortcut("WhatsApp Web", "https://web.whatsapp.com/"),
        SiteShortcut("Microsoft 365", "https://www.microsoft365.com/")
    )

    private fun showCompatibilitySites(aiOnly: Boolean) {
        val sites = if (aiOnly) aiShortcuts else aiShortcuts + otherShortcuts
        AlertDialog.Builder(this).setTitle(if (aiOnly) "AI websites" else "Website compatibility shortcuts")
            .setItems(sites.map { it.name }.toTypedArray()) { _, index ->
                val target = sites[index].url
                current()?.session?.loadUri(target) ?: newTab(target)
            }.setNegativeButton("Close", null).show()
    }

    private fun toggleDesktopMode() {
        desktopMode = !desktopMode
        prefs.edit().putBoolean("desktop_mode", desktopMode).apply()
        current()?.let { tab ->
            tab.session.settings.setUserAgentMode(if (desktopMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            tab.session.settings.setViewportMode(if (desktopMode) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            tab.session.reload()
        }
        Toast.makeText(this, if (desktopMode) "Desktop site mode enabled" else "Mobile site mode enabled", Toast.LENGTH_SHORT).show()
    }
    private fun openInZorvi() {
        val target = current()?.url?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        if (target.isNullOrBlank()) return
        startActivity(Intent(this, ZorviPageActivity::class.java).putExtra(ZorviPageActivity.EXTRA_URL, target))
    }

    private fun updateTabs() {
        val pinnedCount = tabs.count { it.pinned }
        tabsLabel.text = "Tabs " + tabs.size + (if (pinnedCount > 0) " · Pinned " + pinnedCount else "") + " · " + (current()?.group ?: "—")
    }

    private fun searchTabs() {
        val field = EditText(this).apply { hint = "Search tabs by title, URL or group" }
        AlertDialog.Builder(this).setTitle("Search tabs").setView(field)
            .setPositiveButton("Search") { _, _ ->
                val query = field.text.toString().trim()
                val matches = tabs.indices.filter { i ->
                    val tab = tabs[i]
                    query.isBlank() || tab.title.contains(query, true) || tab.url.contains(query, true) || tab.group.contains(query, true)
                }
                if (matches.isEmpty()) Toast.makeText(this, "No matching tabs", Toast.LENGTH_SHORT).show()
                else {
                    val labels = matches.map { i -> (i + 1).toString() + ". " + tabs[i].title.ifBlank { tabs[i].url } }.toTypedArray()
                    AlertDialog.Builder(this).setTitle("Matching tabs").setItems(labels) { _, which ->
                        active = matches[which]; showOnlyActive(); updateTabs()
                    }.show()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showRecentlyClosed() {
        val closed = runCatching { JSONArray(prefs.getString("recently_closed", "[]")) }.getOrDefault(JSONArray())
        val urls = (0 until closed.length()).map { closed.optString(it) }.filter { it.startsWith("http://") || it.startsWith("https://") }.reversed()
        if (urls.isEmpty()) { Toast.makeText(this, "No recently closed tabs", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Recently closed")
            .setItems(urls.toTypedArray()) { _, which -> newTab(urls[which]) }
            .setNegativeButton("Close", null).show()
    }

    private fun togglePinned(tab: Tab) {
        tab.pinned = !tab.pinned
        saveTabs(); updateTabs()
        Toast.makeText(this, if (tab.pinned) "Tab pinned" else "Tab unpinned", Toast.LENGTH_SHORT).show()
    }

    private fun createGroup() {
        val input = EditText(this).apply { hint = "Group name" }
        AlertDialog.Builder(this).setTitle("Create tab group").setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().take(30)
                if (name.isNotEmpty()) {
                    prefs.edit().putString("active_group", name).apply()
                    newTab("https://www.google.com")
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun manageGroups() {
        val groups = (tabs.filter { !it.private }.map { it.group } + "General").distinct().toTypedArray()
        AlertDialog.Builder(this).setTitle("Tab groups").setItems(groups) { _, index ->
            val name = groups[index]
            prefs.edit().putString("active_group", name).apply()
            val found = tabs.indexOfLast { !it.private && it.group == name }
            if (found >= 0) { active = found; showOnlyActive(); updateTabs() } else newTab("https://www.google.com")
        }.setPositiveButton("New group") { _, _ -> createGroup() }.show()
    }

    private fun saveTabs() {
        if (!::root.isInitialized) return
        val saved = JSONArray()
        var savedActive = 0
        tabs.forEachIndexed { index, tab ->
            if (!tab.private) {
                val item = org.json.JSONObject().put("url", tab.url).put("group", tab.group).put("pinned", tab.pinned)
                tab.serializedState?.let { item.put("state", it) }
                saved.put(item)
                if (index <= active) savedActive = saved.length() - 1
            }
        }
        prefs.edit().putString("open_tabs", saved.toString()).putInt("active_tab", savedActive).apply()
    }

    private fun restoreTabs() {
        val saved = runCatching { JSONArray(prefs.getString("open_tabs", "[]")) }.getOrDefault(JSONArray())
        for (i in 0 until saved.length()) {
            val item = saved.optJSONObject(i) ?: continue
            val url = item.optString("url")
            if (!url.startsWith("https://") && !url.startsWith("http://")) continue
            prefs.edit().putString("active_group", item.optString("group", "General")).apply()
            newTab(url, item.optString("state").takeIf { it.isNotBlank() }).also { tabs.lastOrNull()?.pinned = item.optBoolean("pinned", false) }
        }
        if (tabs.isNotEmpty()) {
            active = prefs.getInt("active_tab", tabs.lastIndex).coerceIn(0, tabs.lastIndex)
            showOnlyActive()
            updateTabs()
        }
    }

    private fun queueDownload(url: String, disposition: String?, mime: String?, body: InputStream?) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) { body?.close(); return }
        val headerName = disposition?.substringAfter("filename=", "")?.trim('"')?.takeIf { it.isNotBlank() }
        val rawName = headerName ?: Uri.parse(url).lastPathSegment ?: "download"
        val name = rawName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "download" }
        if (body == null) {
            runCatching {
                val request = DownloadManager.Request(Uri.parse(url)).setTitle(name)
                    .setDescription("Downloading with Nova Browser")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                if (!mime.isNullOrBlank()) request.setMimeType(mime.substringBefore(';'))
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                Toast.makeText(this, "Download queued: " + name, Toast.LENGTH_LONG).show()
            }.onFailure { Toast.makeText(this, "Download failed: " + it.message, Toast.LENGTH_LONG).show() }
            return
        }
        Thread {
            var destination: Uri? = null
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, mime?.substringBefore(';') ?: "application/octet-stream")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    destination = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("Could not create Downloads entry")
                    contentResolver.openOutputStream(destination!!)?.use { output -> body.use { it.copyTo(output) } }
                        ?: throw IllegalStateException("Could not open Downloads output")
                    val complete = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                    contentResolver.update(destination!!, complete, null, null)
                } else {
                    val directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                    val file = File(directory, name)
                    FileOutputStream(file).use { output -> body.use { it.copyTo(output) } }
                }
                runOnUiThread { Toast.makeText(this, "Downloaded: " + name, Toast.LENGTH_LONG).show() }
            } catch (error: Exception) {
                body.close()
                if (destination != null) contentResolver.delete(destination!!, null, null)
                runOnUiThread { Toast.makeText(this, "Download failed: " + error.message, Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun showDownloads() {
        try { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
        catch (_: Exception) { Toast.makeText(this, "Open Downloads in your Files app", Toast.LENGTH_SHORT).show() }
    }

    private fun setBrowserFullScreen(enabled: Boolean) {
        fullScreen = enabled
        if (!::root.isInitialized) return
        root.getChildAt(0)?.visibility = if (enabled) View.GONE else View.VISIBLE
        root.getChildAt(1)?.visibility = if (enabled) View.GONE else View.VISIBLE
        window.decorView.systemUiVisibility = if (enabled)
            (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        else View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
            catch (_: Exception) { Toast.makeText(this, "Picture-in-picture unavailable", Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestFile) {
            val prompt = pendingFilePrompt
            val result = pendingFileResult
            if (prompt != null && result != null) {
                if (resultCode == RESULT_OK && data != null) {
                    val uris = mutableListOf<Uri>()
                    data.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) }
                    if (uris.isEmpty()) data.data?.let { uris.add(it) }
                    if (uris.isNotEmpty()) result.complete(prompt.confirm(this, uris.toTypedArray()))
                    else result.complete(prompt.dismiss())
                } else result.complete(prompt.dismiss())
            }
            pendingFilePrompt = null
            pendingFileResult = null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestWebPermission) {
            val callback = pendingAndroidPermission
            if (callback != null) {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) callback.grant()
                else callback.reject()
            }
            pendingAndroidPermission = null
        }
    }

    private fun showTabs() {
        val names = tabs.mapIndexed { i, tab ->
            "${i + 1}. ${tab.title.ifBlank { tab.url.ifBlank { "New tab" } }}${if (tab.private) " [Private]" else ""}"
        }.toMutableList()
        names.add("＋ New tab")
        names.add("＋ New tab group")
        names.add("Close other tabs")
        names.add("Duplicate current tab")
        names.add(if (current()?.pinned == true) "Unpin current tab" else "Pin current tab")
        names.add("Search tabs")
        names.add("Recently closed")
        AlertDialog.Builder(this).setTitle("Nova tabs").setItems(names.toTypedArray()) { _, which ->
            when (which) {
                in 0 until tabs.size -> { active = which; showOnlyActive(); updateTabs() }
                tabs.size -> newTab(homePage())
                tabs.size + 1 -> createGroup()
                tabs.size + 2 -> closeOtherTabs()
                tabs.size + 3 -> current()?.url?.takeIf { it.startsWith("http") }?.let { newTab(it) }
                tabs.size + 4 -> current()?.let { togglePinned(it) }
                tabs.size + 5 -> searchTabs()
                tabs.size + 6 -> showRecentlyClosed()
            }
        }.setNegativeButton("Close current") { _, _ -> closeTab() }.show()
    }

    private fun closeOtherTabs() {
        val keep = current() ?: return
        tabs.toList().filter { it !== keep }.forEach { tab ->
            tabs.remove(tab)
            tab.view.releaseSession()
            if (tab.session.isOpen) tab.session.close()
        }
        active = tabs.indexOf(keep).coerceAtLeast(0)
        showOnlyActive()
        updateTabs()
        saveTabs()
    }

    private fun closeTab() {
        val removed = current() ?: return
        if (!removed.private && (removed.url.startsWith("https://") || removed.url.startsWith("http://"))) {
            val recently = runCatching { JSONArray(prefs.getString("recently_closed", "[]")) }.getOrDefault(JSONArray())
            val updated = JSONArray().put(removed.url)
            for (i in 0 until recently.length()) {
                val old = recently.optString(i)
                if (old != removed.url && updated.length() < 20) updated.put(old)
            }
            prefs.edit().putString("recently_closed", updated.toString()).apply()
        }
        tabs.removeAt(active)
        removed.session.setActive(false)
        removed.view.releaseSession()
        removed.session.close()
        if (tabs.isEmpty()) newTab("https://www.google.com")
        else { active = active.coerceAtMost(tabs.lastIndex); showOnlyActive(); updateTabs() }
        saveTabs()
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
        if (fullScreen) setBrowserFullScreen(false)
        else if (tab != null && tab.canGoBack) tab.session.goBack() else if (tabs.size > 1) closeTab() else super.onBackPressed()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        saveTabs()
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            tabs.forEachIndexed { index, tab -> if (index != active) tab.session.setActive(false) }
        }
    }

    override fun onResume() {
        super.onResume()
        current()?.session?.setActive(true)
    }

    override fun onPause() {
        current()?.session?.setActive(false)
        saveTabs()
        super.onPause()
    }

    override fun onDestroy() {
        tabs.forEach { tab ->
            tab.view.releaseSession()
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
