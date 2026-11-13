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
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.AllowOrDeny
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.net.URLEncoder
import java.util.Locale
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.Executors
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config

/**
 * Nova Browser main shell backed by Mozilla GeckoView.
 * Each tab owns one GeckoSession; private tabs use Gecko's private-mode session setting.
 */
class MainActivity : Activity() {
    private data class Tab(val session: GeckoSession, val view: GeckoView, var url: String = "", var title: String = "", val private: Boolean = false, val guest: Boolean = false, var group: String = "General", var serializedState: String? = null, var canGoBack: Boolean = false, var canGoForward: Boolean = false, var pinned: Boolean = false)
    private lateinit var root: LinearLayout
    private lateinit var address: EditText
    private lateinit var tabsLabel: TextView
    private lateinit var engine: Spinner
    private val tabs = mutableListOf<Tab>()
    private var active = 0
    private var privateMode = false
    private var guestMode = false
    private var guestReturnTab: Tab? = null
    private var dark = false
    private var desktopMode = false
    private var fullScreen = false
    private var pendingFilePrompt: GeckoSession.PromptDelegate.FilePrompt? = null
    private var pendingFileResult: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = null
    private var pendingAndroidPermission: GeckoSession.PermissionDelegate.Callback? = null
    private val requestFile = 4101
    private val requestWebPermission = 4102
    private val requestWireGuardConfig = 4110
    private val requestWireGuardConsent = 4111
    private val wireGuardStore by lazy { WireGuardConfigStore(this) }
    private val wireGuardBackend by lazy { GoBackend(applicationContext) }
    private val wireGuardExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var wireGuardConnected = false
    private val wireGuardTunnel = object : Tunnel {
        override fun getName(): String = "Nova"
        override fun onStateChange(newState: Tunnel.State) { wireGuardConnected = newState == Tunnel.State.UP }
    }
    private val prefs by lazy { getSharedPreferences("zorvi", Context.MODE_PRIVATE) }
    private val novaAuth by lazy { NovaSupabaseAuth(applicationContext) }
    private val cloudSync by lazy { NovaCloudSync(prefs, novaAuth) }
    private val encryptedVaultSync by lazy { NovaEncryptedVaultSync(novaAuth) }
    private val engines = listOf("Google", "Bing", "DuckDuckGo", "Brave Search")
    private val torController by lazy { NovaTorController(applicationContext) }
    private val runtime: GeckoRuntime get() = getOrCreateRuntime(applicationContext)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dark = prefs.getBoolean("dark_mode", false)
        buildUi()
        novaAuth.restoreSession { restored -> refreshAccountButton(); if (restored) syncCloud(showMessage = false) }
        applyCookiePolicy()
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
        controls.addView(button("＋") { newTab(homePage()) })
        controls.addView(button("←") { current()?.session?.goBack() })
        controls.addView(button("→") { current()?.session?.goForward() })
        controls.addView(button("↻") { current()?.session?.reload() })
        controls.addView(button("★") { bookmark() })
        controls.addView(button("Bookmarks") { showSavedList(false) })
        controls.addView(button("History") { showSavedList(true) })
        controls.addView(button("Passwords") { showPasswordVault() })
        tabsLabel = TextView(this).apply {
            text = "Tabs 0"; gravity = Gravity.CENTER; setPadding(7, 12, 7, 12)
            setOnClickListener { showTabs() }
        }
        controls.addView(tabsLabel)
        controls.addView(button(accountButtonLabel()) { showAccount() })
        controls.addView(button("AI Sites") { showCompatibilitySites(true) })
        controls.addView(button("Sites") { showCompatibilitySites(false) })
        controls.addView(button("Desktop") { toggleDesktopMode() })
        controls.addView(button("Home") { openHome() })
        controls.addView(button("Tools") { showAdvancedTools() })
        controls.addView(button("Shield") { showPrivacyControls() })
        controls.addView(button("Permissions") { showPermissionDashboard() })
        controls.addView(button("Blocklist") { manageBlockedDomains() })
        controls.addView(button("Group") { manageGroups() })
        controls.addView(button("Downloads") { showDownloads() })
        controls.addView(button("PiP") { enterPip() })
        controls.addView(button("VPN") { showVpnStatus() })
        controls.addView(button("Private") { togglePrivate() })
        controls.addView(button(if (guestMode) "End Guest" else "Guest Mode") { if (guestMode) endGuestMode() else startGuestMode() })
        controls.addView(button("☾") {
            dark = !dark
            prefs.edit().putBoolean("dark_mode", dark).apply()
            applyTheme()
        })
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(controls)
        })
        setContentView(root)
        applyTheme()
    }


    private fun accountButtonLabel(): String =
        if (novaAuth.currentEmail() == null) "Account" else "Account ✓"

    private fun showAccount() {
        if (guestMode) {
            Toast.makeText(this, "Nova account actions are disabled in Guest Mode.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!novaAuth.isConfigured) {
            AlertDialog.Builder(this).setTitle("Nova account setup required")
                .setMessage("Connect Nova to Supabase first. Add the Supabase Project URL and publishable anon key to NovaSupabaseConfig.kt, then enable Email authentication in Supabase.")
                .setPositiveButton("OK", null).show()
            return
        }
        val email = novaAuth.currentEmail()
        if (email != null) {
            AlertDialog.Builder(this).setTitle("Nova account")
                .setMessage(email)
                .setPositiveButton("Sync now") { _, _ -> syncCloud(showMessage = true) }
                .setNeutralButton("Sign out") { _, _ ->
                    novaAuth.signOut {
                        runOnUiThread {
                            refreshAccountButton()
                            Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.setNegativeButton("Close", null).show()
        } else showAuthForm()
    }

    private fun showPasswordVault() {
        if (guestMode || current()?.guest == true) {
            Toast.makeText(this, "The password vault is unavailable in Guest Mode.", Toast.LENGTH_SHORT).show()
            return
        }
        if (novaAuth.currentEmail() == null) {
            AlertDialog.Builder(this).setTitle("Sign in required")
                .setMessage("Sign in to a Nova account to back up your encrypted password vault across devices.")
                .setPositiveButton("Account") { _, _ -> showAccount() }
                .setNegativeButton("Close", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Nova Password Vault")
            .setMessage("Your passwords are encrypted on this device before anything is sent to Supabase. A separate sync passphrase is required on every device.")
            .setPositiveButton("Unlock / create") { _, _ -> promptVaultPassphrase(false) }
            .setNeutralButton("Restore from cloud") { _, _ -> promptVaultPassphrase(true) }
            .setNegativeButton("Close", null).show()
    }

    private fun promptVaultPassphrase(restoreFromCloud: Boolean) {
        val input = EditText(this).apply {
            hint = "At least 12 characters"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this).setTitle("Vault sync passphrase")
            .setMessage("This is separate from your Nova login. Nova cannot recover it. Use the same passphrase on every device.")
            .setView(input)
            .setPositiveButton(if (restoreFromCloud) "Decrypt and restore" else "Unlock") { _, _ ->
                val passphrase = input.text.toString().toCharArray()
                if (passphrase.size < 12) {
                    passphrase.fill('\u0000')
                    Toast.makeText(this, "Use at least 12 characters.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                if (restoreFromCloud) {
                    val localPassphrase = passphrase.copyOf()
                    encryptedVaultSync.restore(passphrase) { json, message ->
                        if (json == null) {
                            localPassphrase.fill('\u0000')
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        } else {
                            try {
                                val localEnvelope = NovaVaultCrypto.encrypt(json, localPassphrase)
                                prefs.edit().putString("nova_vault_local", localEnvelope.toString()).apply()
                                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                                showVaultEntries(json, localPassphrase.copyOf())
                            } catch (error: Exception) {
                                Toast.makeText(this, error.message ?: "Could not save the local encrypted vault.", Toast.LENGTH_LONG).show()
                            } finally {
                                localPassphrase.fill('\u0000')
                            }
                        }
                    }
                } else {
                    val local = prefs.getString("nova_vault_local", null)
                    if (local.isNullOrBlank()) {
                        showVaultEntries("{\"entries\":[]}", passphrase)
                    } else {
                        try {
                            val json = NovaVaultCrypto.decrypt(org.json.JSONObject(local), passphrase)
                            showVaultEntries(json, passphrase.copyOf())
                            passphrase.fill('\u0000')
                        } catch (error: Exception) {
                            passphrase.fill('\u0000')
                            Toast.makeText(this, "Could not unlock the local vault. Check your sync passphrase.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .show()
    }

    private fun showVaultEntries(vaultJson: String, passphrase: CharArray) {
        val entries = try { org.json.JSONObject(vaultJson).optJSONArray("entries") ?: JSONArray() }
        catch (_: Exception) { JSONArray() }
        val labels = (0 until entries.length()).map { index ->
            val item = entries.optJSONObject(index) ?: org.json.JSONObject()
            "${item.optString("site", "Unknown site")} — ${item.optString("username")}"
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Saved passwords (${entries.length()})")
            .setItems(labels) { _, index ->
                val item = entries.optJSONObject(index) ?: return@setItems
                AlertDialog.Builder(this).setTitle(item.optString("site"))
                    .setMessage("Username: ${item.optString("username")}\nPassword: ••••••••")
                    .setPositiveButton("Copy password") { _, _ ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Nova password", item.optString("password")))
                        Toast.makeText(this, "Password copied. Clipboard contents may be visible to other apps.", Toast.LENGTH_LONG).show()
                    }
                    .setNeutralButton("Delete") { _, _ ->
                        val updated = JSONArray()
                        for (i in 0 until entries.length()) if (i != index) updated.put(entries.opt(i))
                        val nextPassphrase = passphrase.copyOf()
                        saveVaultAndSync(updated, passphrase)
                        showVaultEntries(org.json.JSONObject().put("entries", updated).toString(), nextPassphrase)
                    }
                    .setNegativeButton("Close", null).show()
            }
            .setPositiveButton("Add password") { _, _ -> showVaultEntryEditor(entries, passphrase) }
            .setNeutralButton("Back up now") { _, _ ->
                saveVaultAndSync(entries, passphrase)
                Toast.makeText(this, "Encrypted vault backup started.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close") { _, _ -> passphrase.fill('\u0000') }
            .show()
    }

    private fun showVaultEntryEditor(entries: JSONArray, passphrase: CharArray) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 12, 32, 4)
        }
        fun field(hintText: String, password: Boolean = false): EditText =
            EditText(this).apply {
                hint = hintText
                if (password) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        val site = field("Website (https://example.com)")
        val username = field("Username or email")
        val password = field("Password", true)
        form.addView(site); form.addView(username); form.addView(password)
        AlertDialog.Builder(this).setTitle("Add password").setView(form)
            .setPositiveButton("Save") { _, _ ->
                if (site.text.isBlank() || password.text.isBlank()) {
                    Toast.makeText(this, "Website and password are required.", Toast.LENGTH_LONG).show()
                } else {
                    val updated = JSONArray(entries.toString())
                    updated.put(org.json.JSONObject()
                        .put("site", site.text.toString().trim())
                        .put("username", username.text.toString().trim())
                        .put("password", password.text.toString()))
                    val nextPassphrase = passphrase.copyOf()
                    saveVaultAndSync(updated, passphrase)
                    showVaultEntries(org.json.JSONObject().put("entries", updated).toString(), nextPassphrase)
                }
            }
            .setNegativeButton("Cancel") { _, _ -> passphrase.fill('\u0000') }
            .show()
    }

    private fun saveVaultAndSync(entries: JSONArray, passphrase: CharArray) {
        val json = org.json.JSONObject().put("entries", entries).toString()
        val localPassphrase = passphrase.copyOf()
        val cloudPassphrase = passphrase.copyOf()
        try {
            val localEnvelope = NovaVaultCrypto.encrypt(json, localPassphrase)
            prefs.edit().putString("nova_vault_local", localEnvelope.toString()).apply()
            encryptedVaultSync.backup(json, cloudPassphrase) { _, message ->
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        } catch (error: Exception) {
            cloudPassphrase.fill('\u0000')
            Toast.makeText(this, error.message ?: "Could not encrypt the password vault.", Toast.LENGTH_LONG).show()
        } finally {
            localPassphrase.fill('\u0000')
            passphrase.fill('\u0000')
        }
    }

    private fun syncCloud(showMessage: Boolean) {
        if (guestMode || novaAuth.currentEmail() == null) return
        saveTabs()
        val tabsBeforeSync = prefs.getString("open_tabs", "[]")
        cloudSync.sync { ok, message ->
            if (ok) {
                val tabsAfterSync = prefs.getString("open_tabs", "[]")
                if (tabsBeforeSync != tabsAfterSync) {
                    tabs.toList().forEach { tab ->
                        tab.view.releaseSession()
                        if (tab.session.isOpen) tab.session.close()
                    }
                    tabs.clear()
                    active = 0
                    restoreTabs()
                    if (tabs.isEmpty()) newTab(homePage())
                }
                dark = prefs.getBoolean("dark_mode", dark)
                if (::engine.isInitialized) engine.setSelection(prefs.getInt("engine", 0).coerceIn(0, engines.lastIndex))
                applyTheme()
            }
            if (showMessage) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshAccountButton() {
        if (!::root.isInitialized || root.childCount < 2) return
        val controls = (root.getChildAt(1) as? HorizontalScrollView)?.getChildAt(0) as? LinearLayout ?: return
        for (i in 0 until controls.childCount) {
            val child = controls.getChildAt(i)
            if (child is Button && (child.text == "Account" || child.text == "Account ✓")) {
                child.text = accountButtonLabel()
                break
            }
        }
    }

    private fun showAuthForm() {
        val email = EditText(this).apply {
            hint = "Email address"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            isSingleLine = true
        }
        val password = EditText(this).apply {
            hint = "Password (at least 8 characters)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        val confirm = EditText(this).apply {
            hint = "Confirm password (for new account)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        val otpLogin = Button(this).apply { text = "Sign in with email OTP"; isAllCaps = false }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 8, 28, 0)
            addView(email); addView(password); addView(confirm); addView(otpLogin)
        }
        val dialog = AlertDialog.Builder(this).setTitle("Sign in to Nova")
            .setMessage("Use your email and password, create an account, or receive a six-digit email code.")
            .setView(fields).setPositiveButton("Sign in", null)
            .setNeutralButton("Create account", null).setNegativeButton("Forgot password", null).create()
        dialog.setOnShowListener {
            otpLogin.setOnClickListener {
                val address = email.text.toString().trim()
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
                    email.error = "Enter a valid email address"; return@setOnClickListener
                }
                otpLogin.isEnabled = false
                novaAuth.requestOtp(address) { ok, message ->
                    runOnUiThread {
                        otpLogin.isEnabled = true
                        if (ok) { dialog.dismiss(); showEmailOtpVerification(address) }
                        else AlertDialog.Builder(this).setTitle("Email OTP").setMessage(message ?: "Could not send code.")
                            .setPositiveButton("OK", null).show()
                    }
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val address = email.text.toString().trim()
                val secret = password.text.toString()
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
                    email.error = "Enter a valid email"; return@setOnClickListener
                }
                if (secret.isBlank()) { password.error = "Enter your password"; return@setOnClickListener }
                setAuthBusy(dialog, true)
                novaAuth.signIn(address, secret) { ok, message ->
                    runOnUiThread {
                        if (ok) {
                            dialog.dismiss(); refreshAccountButton()
                            syncCloud(showMessage = true)
                            Toast.makeText(this, "Welcome back to Nova", Toast.LENGTH_SHORT).show()
                        } else {
                            setAuthBusy(dialog, false)
                            showAuthError("Sign-in failed", message)
                        }
                    }
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val address = email.text.toString().trim()
                val secret = password.text.toString()
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
                    email.error = "Enter a valid email"; return@setOnClickListener
                }
                if (secret.length < 8) { password.error = "Use at least 8 characters"; return@setOnClickListener }
                if (secret != confirm.text.toString()) { confirm.error = "Passwords do not match"; return@setOnClickListener }
                setAuthBusy(dialog, true)
                novaAuth.signUp(address, secret) { ok, message ->
                    runOnUiThread {
                        dialog.dismiss()
                        AlertDialog.Builder(this).setTitle(if (ok) "Check your email" else "Could not create account")
                            .setMessage(message ?: if (ok) "A confirmation email was sent. Confirm your email, then sign in." else "Please try again.")
                            .setPositiveButton("OK", null).show()
                    }
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                val address = email.text.toString().trim()
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
                    email.error = "Enter your account email"; return@setOnClickListener
                }
                setAuthBusy(dialog, true)
                novaAuth.resetPassword(address) { ok, message ->
                    runOnUiThread {
                        dialog.dismiss()
                        if (ok) {
                            Toast.makeText(this, message ?: "If the account exists, a reset code was sent.", Toast.LENGTH_LONG).show()
                            showPasswordResetOtpVerification(address)
                        } else showAuthError("Could not send reset code", message)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showEmailOtpVerification(emailAddress: String) {
        val code = EditText(this).apply {
            hint = "6-digit email code"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            isSingleLine = true
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }
        val dialog = AlertDialog.Builder(this).setTitle("Verify email OTP")
            .setMessage("Enter the six-digit code sent to $emailAddress.")
            .setView(code).setPositiveButton("Verify", null)
            .setNegativeButton("Cancel", null).setNeutralButton("Resend code", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entered = code.text.toString().trim()
                if (!entered.matches(Regex("\\d{6}"))) { code.error = "Enter all 6 digits"; return@setOnClickListener }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                novaAuth.verifyOtp(emailAddress, entered) { ok, message ->
                    runOnUiThread {
                        if (ok) {
                            dialog.dismiss(); refreshAccountButton()
                            syncCloud(showMessage = true)
                            Toast.makeText(this, "Signed in to Nova", Toast.LENGTH_LONG).show()
                        } else {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            AlertDialog.Builder(this).setTitle("OTP verification failed")
                                .setMessage(message ?: "The code is invalid or expired.")
                                .setPositiveButton("OK", null).show()
                        }
                    }
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = false
                novaAuth.requestOtp(emailAddress) { ok, message ->
                    runOnUiThread {
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = true
                        Toast.makeText(this, message ?: if (ok) "A new code was sent." else "Could not resend code.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showPasswordResetOtpVerification(emailAddress: String) {
        val code = EditText(this).apply {
            hint = "6-digit password-reset code"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            isSingleLine = true
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }
        val dialog = AlertDialog.Builder(this).setTitle("Reset Nova password")
            .setMessage("Enter the six-digit recovery code sent to $emailAddress.")
            .setView(code).setPositiveButton("Verify code", null)
            .setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entered = code.text.toString().trim()
                if (!entered.matches(Regex("\\d{6}"))) {
                    code.error = "Enter all 6 digits"
                    return@setOnClickListener
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                novaAuth.verifyRecoveryOtp(emailAddress, entered) { ok, message ->
                    runOnUiThread {
                        if (ok) {
                            dialog.dismiss()
                            val newPassword = EditText(this).apply {
                                hint = "New password (at least 8 characters)"
                                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                                isSingleLine = true
                            }
                            val passwordDialog = AlertDialog.Builder(this)
                                .setTitle("Choose a new password")
                                .setView(newPassword)
                                .setPositiveButton("Update password", null)
                                .setNegativeButton("Cancel", null).create()
                            passwordDialog.setOnShowListener {
                                passwordDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                                    val value = newPassword.text.toString()
                                    if (value.length < 8) {
                                        newPassword.error = "Use at least 8 characters"
                                        return@setOnClickListener
                                    }
                                    passwordDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                                    novaAuth.updatePassword(value) { updated, updateMessage ->
                                        runOnUiThread {
                                            passwordDialog.dismiss()
                                            if (updated) {
                                                refreshAccountButton()
                                                Toast.makeText(this, updateMessage, Toast.LENGTH_LONG).show()
                                            } else showAuthError("Password update failed", updateMessage)
                                        }
                                    }
                                }
                            }
                            passwordDialog.show()
                        } else {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                            showAuthError("Recovery code verification failed", message)
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun setAuthBusy(dialog: AlertDialog, busy: Boolean) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !busy
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = !busy
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = !busy
    }

    private fun showAuthError(title: String, message: String?) {
        AlertDialog.Builder(this).setTitle(title)
            .setMessage(message ?: "Please try again.")
            .setPositiveButton("OK", null).show()
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 11f; minWidth = 0
        setPadding(3, 0, 3, 0); setOnClickListener { action() }
    }

    private fun current(): Tab? = tabs.getOrNull(active)

    private fun homePage(): String {
        val saved = prefs.getString("home_page", null)
        // Migrate the previous built-in Google start page to the new Kali-themed start page.
        return if (saved.isNullOrBlank() || saved == "https://www.google.com") {
            "file:///android_asset/home.html"
        } else saved
    }

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
                2 -> translateCurrentPageOffline()
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
        val tab = current() ?: return
        val url = tab.url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(this, "Open a webpage first", Toast.LENGTH_SHORT).show(); return
        }
        Toast.makeText(this, "Extracting page text…", Toast.LENGTH_SHORT).show()
        tab.session.sessionPageExtractor.pageContent.then<Void> { content ->
            val excerpt = content.orEmpty().take(9000)
            runOnUiThread {
                if (excerpt.isBlank()) Toast.makeText(this, "Could not extract readable page text", Toast.LENGTH_LONG).show()
                else chooseAiProvider("Summarize this page using the extracted content.\nURL: $url\n\nPAGE CONTENT:\n$excerpt", "Summarize with")
            }
            null
        }.exceptionally<Void> { error ->
            runOnUiThread { Toast.makeText(this, "Page extraction failed: " + (error.message ?: "unknown error"), Toast.LENGTH_LONG).show() }
            null
        }
    }

    private fun showReaderContent(title: String, content: String) {
        val text = TextView(this).apply {
            text = content
            textSize = prefs.getInt("reader_text_size", 17).coerceIn(12, 28).toFloat()
            val night = prefs.getBoolean("reader_dark", false)
            setTextColor(if (night) Color.WHITE else Color.BLACK)
            setBackgroundColor(if (night) Color.rgb(28, 28, 28) else Color.WHITE)
            setPadding(24, 20, 24, 24)
        }
        val scroll = ScrollView(this).apply { addView(text) }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("A−") {
                val size = (prefs.getInt("reader_text_size", 17) - 1).coerceAtLeast(12)
                prefs.edit().putInt("reader_text_size", size).apply(); text.textSize = size.toFloat()
            })
            addView(button("A+") {
                val size = (prefs.getInt("reader_text_size", 17) + 1).coerceAtMost(28)
                prefs.edit().putInt("reader_text_size", size).apply(); text.textSize = size.toFloat()
            })
            addView(button("Theme") {
                val night = !prefs.getBoolean("reader_dark", false)
                prefs.edit().putBoolean("reader_dark", night).apply()
                text.setTextColor(if (night) Color.WHITE else Color.BLACK)
                text.setBackgroundColor(if (night) Color.rgb(28, 28, 28) else Color.WHITE)
            })
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(controls)
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        AlertDialog.Builder(this).setTitle(title).setView(layout).setPositiveButton("Done", null).show()
    }

    private fun openReaderView() {
        val tab = current() ?: return
        if (!tab.url.startsWith("http://") && !tab.url.startsWith("https://")) {
            Toast.makeText(this, "Open an article first", Toast.LENGTH_SHORT).show(); return
        }
        tab.session.sessionPageExtractor.pageContent.then<Void> { content ->
            runOnUiThread {
                val article = content.orEmpty()
                if (article.isBlank()) Toast.makeText(this, "No readable article text found", Toast.LENGTH_LONG).show()
                else showReaderContent(tab.title.ifBlank { "Reader view" }, article)
            }
            null
        }.exceptionally<Void> { error ->
            runOnUiThread { Toast.makeText(this, "Reader extraction failed: " + error.message, Toast.LENGTH_LONG).show() }
            null
        }
    }

    private fun translateCurrentPageOffline() {
        if (current()?.guest == true) {
            Toast.makeText(this, "On-device model downloads are disabled in Guest Mode.", Toast.LENGTH_LONG).show()
            return
        }
        val tab = current() ?: return
        if (!tab.url.startsWith("http://") && !tab.url.startsWith("https://")) {
            Toast.makeText(this, "Open a webpage first", Toast.LENGTH_SHORT).show(); return
        }
        val target = prefs.getString("translation_language", "en") ?: "en"
        val targetCode = if (target == "zh-CN") TranslateLanguage.CHINESE else target
        if (targetCode == "as") {
            Toast.makeText(this, "Assamese is not available offline; using online translation.", Toast.LENGTH_LONG).show()
            translateCurrentPage(); return
        }
        Toast.makeText(this, "Extracting page text…", Toast.LENGTH_SHORT).show()
        tab.session.sessionPageExtractor.pageContent.then<Void> { content ->
            val pageText = content.orEmpty().take(12000)
            if (pageText.isBlank()) {
                runOnUiThread { Toast.makeText(this, "No readable text found", Toast.LENGTH_LONG).show() }
            } else {
                LanguageIdentification.getClient().identifyLanguage(pageText.take(3000))
                    .addOnSuccessListener { source ->
                        if (source == "und") {
                            runOnUiThread { Toast.makeText(this, "Could not identify source language", Toast.LENGTH_LONG).show() }
                        } else {
                            val options = TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(targetCode).build()
                            val translator = Translation.getClient(options)
                            translator.downloadModelIfNeeded().addOnSuccessListener {
                                translator.translate(pageText).addOnSuccessListener { translated ->
                                    runOnUiThread { showReaderContent("Offline translation", translated) }
                                    translator.close()
                                }.addOnFailureListener { error ->
                                    translator.close()
                                    runOnUiThread { Toast.makeText(this, "Translation failed: " + error.message, Toast.LENGTH_LONG).show() }
                                }
                            }.addOnFailureListener {
                                translator.close()
                                runOnUiThread { Toast.makeText(this, "Could not download language model. Connect once to download it.", Toast.LENGTH_LONG).show() }
                            }
                        }
                    }.addOnFailureListener { error ->
                        runOnUiThread { Toast.makeText(this, "Language detection failed: " + error.message, Toast.LENGTH_LONG).show() }
                    }
            }
            null
        }.exceptionally<Void> { error ->
            runOnUiThread { Toast.makeText(this, "Page extraction failed: " + error.message, Toast.LENGTH_LONG).show() }
            null
        }
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



    private fun blockedDomains(): Set<String> = prefs.getStringSet("blocked_domains", emptySet())?.toSet() ?: emptySet()

    private fun isExternalScheme(raw: String): Boolean {
        val scheme = runCatching { Uri.parse(raw).scheme?.lowercase(Locale.ROOT) }.getOrNull() ?: return false
        return scheme in setOf("mailto", "tel", "sms", "smsto", "mms", "mmsto", "geo", "market", "intent")
    }

    private fun openExternalUri(raw: String) {
        try {
            val scheme = Uri.parse(raw).scheme?.lowercase(Locale.ROOT)
            val intent = when (scheme) {
                "tel" -> Intent(Intent.ACTION_DIAL, Uri.parse(raw))
                "mailto", "sms", "smsto", "mms", "mmsto" -> Intent(Intent.ACTION_SENDTO, Uri.parse(raw))
                "geo", "market" -> Intent(Intent.ACTION_VIEW, Uri.parse(raw))
                "intent" -> Intent.parseUri(raw, Intent.URI_INTENT_SCHEME).apply {
                    component = null
                    selector = null
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                else -> return
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "No installed app can open this link.", Toast.LENGTH_LONG).show()
        }
    }

    private fun isDomainBlocked(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host?.lowercase(Locale.ROOT) }.getOrNull() ?: return false
        return blockedDomains().any { host == it || host.endsWith("." + it) }
    }

    private fun manageBlockedDomains() {
        val field = EditText(this).apply { hint = "ads.example.com"; isSingleLine = true }
        val existing = blockedDomains().sorted()
        AlertDialog.Builder(this).setTitle("Domain blocklist")
            .setMessage("Adds your own domain-level rules on top of Nova's EasyList and EasyPrivacy network filter subscriptions. These rules block matching page and iframe navigations.")
            .setView(field)
            .setPositiveButton("Add domain") { _, _ ->
                val host = field.text.toString().trim().lowercase(Locale.ROOT).removePrefix("https://").removePrefix("http://").substringBefore("/").substringBefore(":")
                if (host.contains(".") && host.matches(Regex("^[a-z0-9.-]+[.][a-z]{2,}$"))) {
                    val updated = blockedDomains().toMutableSet().apply { add(host) }
                    prefs.edit().putStringSet("blocked_domains", updated).apply()
                    Toast.makeText(this, "Added " + host, Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this, "Enter a valid domain", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Manage") { _, _ ->
                if (existing.isEmpty()) Toast.makeText(this, "Blocklist is empty", Toast.LENGTH_SHORT).show()
                else AlertDialog.Builder(this).setTitle("Blocked domains").setItems(existing.toTypedArray()) { _, which ->
                    val updated = blockedDomains().toMutableSet().apply { remove(existing[which]) }
                    prefs.edit().putStringSet("blocked_domains", updated).apply()
                }.setNegativeButton("Close", null).show()
            }.setNegativeButton("Close", null).show()
    }

    private fun showPermissionDashboard() {
        val entries = prefs.all.filter { it.key.startsWith("site_permission_") }
        if (entries.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Site permissions").setMessage("No remembered site permissions yet. You can choose Remember when a site requests access.")
                .setPositiveButton("OK", null).show()
            return
        }
        val permissionKeys = entries.map { it.key }
        val labels = entries.map { entry ->
            val parts = entry.key.removePrefix("site_permission_").split("_")
            val site = parts.dropLast(1).joinToString("_")
            val state = if (entry.value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW) "Allowed" else "Blocked"
            site + " — " + state
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Remembered site permissions")
            .setItems(labels) { _, index ->
                val key = permissionKeys[index]
                AlertDialog.Builder(this).setTitle(labels[index]).setMessage("Remove this saved decision? The site will ask again next time.")
                    .setPositiveButton("Forget") { _, _ -> prefs.edit().remove(key).apply(); showPermissionDashboard() }
                    .setNegativeButton("Keep", null).show()
            }.setNeutralButton("Clear all") { _, _ ->
                val edit = prefs.edit()
                entries.forEach { edit.remove(it.key) }
                edit.apply()
            }.setNegativeButton("Close", null).show()
    }

    private fun showPrivacyControls() {
        val enabled = prefs.getBoolean("tracking_protection", true)
        val message = "GeckoView tracking protection is " + (if (enabled) "enabled." else "disabled.") +
            "\\n\\nNova uses EasyList and EasyPrivacy filter subscriptions for network and cosmetic ad blocking. Lists refresh about once per day when online; previously saved lists remain active between updates."
        AlertDialog.Builder(this).setTitle("Privacy and protection").setMessage(message)
            .setPositiveButton(if (enabled) "Turn off" else "Turn on") { _, _ ->
                prefs.edit().putBoolean("tracking_protection", !enabled).apply()
                Toast.makeText(this, "Protection setting saved. It applies to new tabs.", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("Website data") { _, _ -> showWebsiteDataControls() }
            .setNegativeButton("Close", null).show()
    }

    private fun applyCookiePolicy() {
        val defaultPolicy = org.mozilla.geckoview.ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
        val policy = prefs.getInt("cookie_policy", defaultPolicy)
        runtime.settings.contentBlocking.setCookieBehavior(policy)
        runtime.settings.contentBlocking.setCookieBehaviorPrivateMode(policy)
    }

    private fun chooseCookiePolicy() {
        val labels = arrayOf(
            "Block known trackers (recommended)",
            "Block all third-party cookies",
            "Allow all cookies",
            "Block all cookies"
        )
        val values = intArrayOf(
            org.mozilla.geckoview.ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS,
            org.mozilla.geckoview.ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY,
            org.mozilla.geckoview.ContentBlocking.CookieBehavior.ACCEPT_ALL,
            org.mozilla.geckoview.ContentBlocking.CookieBehavior.ACCEPT_NONE
        )
        val currentPolicy = prefs.getInt("cookie_policy", values[0])
        val selected = values.indexOf(currentPolicy).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle("Cookie policy")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                val policy = values[which]
                prefs.edit().putInt("cookie_policy", policy).apply()
                runtime.settings.contentBlocking.setCookieBehavior(policy)
                runtime.settings.contentBlocking.setCookieBehaviorPrivateMode(policy)
                dialog.dismiss()
                Toast.makeText(this, "Cookie policy updated. Reload pages to apply changes.", Toast.LENGTH_LONG).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showWebsiteDataControls() {
        val actions = arrayOf(
            "Cookie policy",
            "Clear cookies and site data",
            "Clear cached files",
            "Clear all browsing data"
        )
        val descriptions = arrayOf(
            "Signs you out of websites and clears cookies, local storage, site permissions and related site data.",
            "Removes cached images and files. Website sign-ins should remain.",
            "Clears Gecko cookies, site storage, permissions and caches, plus Nova history, bookmarks and saved tabs. Downloaded files are not deleted."
        )
        AlertDialog.Builder(this).setTitle("Website data")
            .setItems(actions) { _, which ->
                if (which == 0) {
                    chooseCookiePolicy()
                } else {
                    val index = which - 1
                    AlertDialog.Builder(this).setTitle(actions[which]).setMessage(descriptions[index])
                        .setPositiveButton("Clear") { _, _ ->
                            val flags = when (index) {
                                0 -> org.mozilla.geckoview.StorageController.ClearFlags.SITE_DATA
                                1 -> org.mozilla.geckoview.StorageController.ClearFlags.ALL_CACHES
                                else -> org.mozilla.geckoview.StorageController.ClearFlags.ALL
                            }
                            clearGeckoData(flags, index == 2)
                        }.setNegativeButton("Cancel", null).show()
                }
            }.setNegativeButton("Close", null).show()
    }

    private fun clearGeckoData(flags: Long, clearNovaData: Boolean) {
        val reopenUrl = current()?.url?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: homePage()
        tabs.toList().forEach { tab ->
            tab.view.releaseSession()
            if (tab.session.isOpen) tab.session.close()
        }
        tabs.clear()
        active = 0
        if (root.childCount > 2) root.removeViews(2, root.childCount - 2)
        if (clearNovaData) {
            val edit = prefs.edit().remove("history").remove("bookmarks").remove("open_tabs").remove("recently_closed")
            prefs.all.keys.filter { it.startsWith("site_permission_") }.forEach { edit.remove(it) }
            edit.apply()
        }
        runtime.storageController.clearData(flags).then<Void> {
            runOnUiThread {
                Toast.makeText(this, "Website data cleared.", Toast.LENGTH_LONG).show()
                newTab(reopenUrl)
            }
            null
        }.exceptionally<Void> { error ->
            runOnUiThread {
                Toast.makeText(this, "Could not clear website data: " + (error.message ?: "unknown error"), Toast.LENGTH_LONG).show()
                newTab(reopenUrl)
            }
            null
        }
    }

    private fun newTab(url: String = homePage(), restoredState: String? = null) {
        desktopMode = prefs.getBoolean("desktop_mode", false)
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(privateMode || guestMode)
            .useTrackingProtection(prefs.getBoolean("tracking_protection", true))
            .allowJavascript(true)
            .userAgentMode(if (desktopMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(if (desktopMode) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build()
        val session = GeckoSession(settings)
        val view = GeckoView(this).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setAutofillEnabled(true)
        }
        val isGuestTab = guestMode
        val tab = Tab(session, view, private = privateMode || isGuestTab, guest = isGuestTab, group = if (isGuestTab) "Guest" else if (privateMode) "Private" else (prefs.getString("active_group", "General") ?: "General"))
        session.setContentDelegate(object : GeckoSession.ContentDelegate {
            override fun onExternalResponse(session: GeckoSession, response: org.mozilla.geckoview.WebResponse) {
                if (tab.private || tab.guest) {
                    response.body?.close()
                    runOnUiThread { Toast.makeText(this@MainActivity, "Downloads are disabled in private and guest sessions.", Toast.LENGTH_LONG).show() }
                    return
                }
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
            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                if (isExternalScheme(request.uri)) {
                    if (tab.guest) return GeckoResult.deny()
                    // Only a direct user gesture may launch another Android app.
                    if (request.hasUserGesture) openExternalUri(request.uri)
                    return GeckoResult.deny()
                }
                return if (isDomainBlocked(request.uri)) GeckoResult.deny() else null
            }
            override fun onSubframeLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                return if (isDomainBlocked(request.uri) || isExternalScheme(request.uri)) GeckoResult.deny() else null
            }
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
                if (!tab.private && !tab.guest && (next.startsWith("https://", true) || next.startsWith("http://", true))) {
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
                if (tab.guest || permissions.isNullOrEmpty()) { callback.reject(); return }
                pendingAndroidPermission = callback
                requestPermissions(permissions, requestWebPermission)
            }
            override fun onContentPermissionRequest(session: GeckoSession, perm: GeckoSession.PermissionDelegate.ContentPermission): GeckoResult<Int> {
                val result = GeckoResult<Int>()
                if (tab.guest) {
                    result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                    return result
                }
                val host = runCatching { Uri.parse(perm.uri).host }.getOrNull() ?: "this website"
                val permissionKey = "site_permission_" + host + "_" + perm.permission
                val remembered = if (tab.guest) -1 else prefs.getInt(permissionKey, -1)
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
                            if (remember.isChecked && !tab.guest) prefs.edit().putInt(permissionKey, value).apply()
                            result.complete(value)
                        }
                        .setNegativeButton("Block") { _, _ ->
                            val value = GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                            if (remember.isChecked) prefs.edit().putInt(permissionKey, value).apply()
                            result.complete(value)
                        }
                        .setOnCancelListener { result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY) }.create()
                    dialog.show()
                }
                return result
            }
            override fun onMediaPermissionRequest(session: GeckoSession, uri: String, video: Array<GeckoSession.PermissionDelegate.MediaSource>?, audio: Array<GeckoSession.PermissionDelegate.MediaSource>?, callback: GeckoSession.PermissionDelegate.MediaCallback) {
                if (tab.guest) { callback.reject(); return }
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
                if (!tab.private && !tab.guest) saveTabs()
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
            hasScheme && current()?.guest == true -> {
                Toast.makeText(this, "External app links are disabled in Guest Mode.", Toast.LENGTH_SHORT).show()
                return
            }
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
        val input = EditText(this).apply { hint = "Group name"; isSingleLine = true }
        AlertDialog.Builder(this).setTitle("Create tab group").setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().take(30)
                val exists = tabs.any { !it.private && it.group.equals(name, true) }
                if (name.isNotEmpty() && !exists) {
                    prefs.edit().putString("active_group", name).apply()
                    newTab(homePage())
                } else if (exists) Toast.makeText(this, "A group with that name already exists", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun manageGroups() {
        val groups = (tabs.filter { !it.private }.map { it.group } + "General").distinct().sorted()
        AlertDialog.Builder(this).setTitle("Tab groups").setItems(groups.toTypedArray()) { _, index ->
            showGroupActions(groups[index])
        }.setPositiveButton("New group") { _, _ -> createGroup() }.setNegativeButton("Close", null).show()
    }

    private fun showGroupActions(name: String) {
        val count = tabs.count { !it.private && it.group == name }
        val actions = arrayOf("Open group ($count tabs)", "Move current tab here", "Rename group", "Delete group")
        AlertDialog.Builder(this).setTitle(name).setItems(actions) { _, which ->
            when (which) {
                0 -> showGroupTabs(name)
                1 -> moveCurrentTabToGroup(name)
                2 -> renameGroup(name)
                3 -> deleteGroup(name)
            }
        }.setNegativeButton("Back") { _, _ -> manageGroups() }.show()
    }

    private fun showGroupTabs(name: String) {
        val matches = tabs.indices.filter { !tabs[it].private && !tabs[it].guest && tabs[it].group == name }
        if (matches.isEmpty()) {
            prefs.edit().putString("active_group", name).apply()
            newTab(homePage())
            return
        }
        val labels = matches.map { index ->
            val tab = tabs[index]
            tab.title.ifBlank { tab.url.ifBlank { "New tab" } }
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("${name} — ${matches.size} tabs")
            .setItems(labels) { _, which ->
                active = matches[which]
                prefs.edit().putString("active_group", name).apply()
                showOnlyActive()
                updateTabs()
            }
            .setPositiveButton("New tab in group") { _, _ ->
                prefs.edit().putString("active_group", name).apply()
                privateMode = false
                newTab(homePage())
            }
            .setNegativeButton("Back") { _, _ -> showGroupActions(name) }
            .show()
    }

    private fun moveCurrentTabToGroup(name: String) {
        val tab = current() ?: return
        if (tab.private) { Toast.makeText(this, "Private tabs cannot join regular groups", Toast.LENGTH_SHORT).show(); return }
        tab.group = name
        prefs.edit().putString("active_group", name).apply()
        saveTabs(); updateTabs()
        Toast.makeText(this, "Tab moved to " + name, Toast.LENGTH_SHORT).show()
    }

    private fun renameGroup(oldName: String) {
        val input = EditText(this).apply { setText(oldName); isSingleLine = true }
        AlertDialog.Builder(this).setTitle("Rename group").setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim().take(30)
                if (newName.isNotEmpty() && !tabs.any { !it.private && it.group.equals(newName, true) && it.group != oldName }) {
                    tabs.filter { !it.private && it.group == oldName }.forEach { it.group = newName }
                    if (prefs.getString("active_group", "General") == oldName) prefs.edit().putString("active_group", newName).apply()
                    saveTabs(); updateTabs()
                } else Toast.makeText(this, "Invalid or duplicate group name", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun deleteGroup(name: String) {
        if (name == "General") { Toast.makeText(this, "General group cannot be deleted", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Delete " + name + "?")
            .setMessage("Tabs in this group will be moved to General. No tabs will be closed.")
            .setPositiveButton("Delete") { _, _ ->
                tabs.filter { !it.private && it.group == name }.forEach { it.group = "General" }
                if (prefs.getString("active_group", "General") == name) prefs.edit().putString("active_group", "General").apply()
                saveTabs(); updateTabs()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun saveTabs() {
        if (!::root.isInitialized || guestMode) return
        val saved = JSONArray()
        var savedActive = 0
        tabs.forEachIndexed { index, tab ->
            if (!tab.private && !tab.guest) {
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
                    .setAllowedOverMetered(true).setAllowedOverRoaming(false)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                if (!mime.isNullOrBlank()) request.setMimeType(mime.substringBefore(';'))
                val id = (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                val records = runCatching { JSONArray(prefs.getString("download_ids", "[]")) }.getOrDefault(JSONArray())
                records.put(id)
                while (records.length() > 100) records.remove(0)
                prefs.edit().putString("download_ids", records.toString()).apply()
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
                    val direct = runCatching { JSONArray(prefs.getString("direct_downloads", "[]")) }.getOrDefault(JSONArray())
                    direct.put(org.json.JSONObject().put("name", name).put("uri", destination.toString())
                        .put("mime", mime?.substringBefore(';') ?: "application/octet-stream"))
                    while (direct.length() > 100) direct.remove(0)
                    prefs.edit().putString("direct_downloads", direct.toString()).apply()
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
        if (current()?.guest == true) {
            Toast.makeText(this, "Downloads are hidden in Guest Mode.", Toast.LENGTH_SHORT).show()
            return
        }
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val ids = runCatching { JSONArray(prefs.getString("download_ids", "[]")) }.getOrDefault(JSONArray())
        val entries = mutableListOf<Long>()
        val labels = mutableListOf<String>()
        val direct = runCatching { JSONArray(prefs.getString("direct_downloads", "[]")) }.getOrDefault(JSONArray())
        val directUris = mutableListOf<Uri>()
        val cursor = manager.query(DownloadManager.Query())
        cursor?.use {
            val idCol = it.getColumnIndex(DownloadManager.COLUMN_ID)
            val titleCol = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val statusCol = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val bytesCol = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalCol = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val reasonCol = it.getColumnIndex(DownloadManager.COLUMN_REASON)
            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                if ((0 until ids.length()).any { n -> ids.optLong(n) == id }) {
                    entries.add(id)
                    val status = when (it.getInt(statusCol)) {
                        DownloadManager.STATUS_PENDING -> "Pending"
                        DownloadManager.STATUS_RUNNING -> "Downloading"
                        DownloadManager.STATUS_PAUSED -> "Paused"
                        DownloadManager.STATUS_SUCCESSFUL -> "Complete"
                        else -> "Failed"
                    }
                    val bytes = it.getLong(bytesCol).coerceAtLeast(0)
                    val total = it.getLong(totalCol)
                    val progress = if (total > 0) " · " + (bytes * 100 / total) + "%" else ""
                    val reason = if (it.getInt(statusCol) == DownloadManager.STATUS_FAILED) " · Error " + it.getInt(reasonCol) else ""
                    labels.add((it.getString(titleCol) ?: "Download") + " — " + status + progress + reason)
                }
            }
        }
        for (i in 0 until direct.length()) {
            val item = direct.optJSONObject(i) ?: continue
            val uri = runCatching { Uri.parse(item.optString("uri")) }.getOrNull() ?: continue
            if (uri.scheme == "content") {
                directUris.add(uri)
                labels.add(item.optString("name", "Downloaded file") + " — Complete")
            }
        }
        if (labels.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Downloads").setMessage("No Nova-managed downloads found.")
                .setPositiveButton("Open Android downloads") { _, _ ->
                    runCatching { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
                }.setNegativeButton("Close", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Downloads").setItems(labels.toTypedArray()) { _, which ->
            if (which >= entries.size) {
                val directIndex = which - entries.size
                val uri = directUris[directIndex]
                val item = (0 until direct.length()).mapNotNull { direct.optJSONObject(it) }
                    .firstOrNull { it.optString("uri") == uri.toString() }
                AlertDialog.Builder(this).setItems(arrayOf("Open file", "Delete file")) { _, action ->
                    if (action == 0) {
                        val mime = item?.optString("mime", "application/octet-stream") ?: "application/octet-stream"
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                            .onFailure { Toast.makeText(this, "No app can open this file", Toast.LENGTH_SHORT).show() }
                    } else AlertDialog.Builder(this).setMessage("Delete this downloaded file?")
                        .setPositiveButton("Delete") { _, _ ->
                            contentResolver.delete(uri, null, null)
                            val updated = JSONArray()
                            for (i in 0 until direct.length()) {
                                val old = direct.optJSONObject(i)
                                if (old?.optString("uri") != uri.toString()) updated.put(old)
                            }
                            prefs.edit().putString("direct_downloads", updated.toString()).apply()
                        }.setNegativeButton("Cancel", null).show()
                }.show()
                return@setItems
            }
            val id = entries[which]
            AlertDialog.Builder(this).setItems(arrayOf("Open file", "Remove from Nova list", "Delete downloaded file")) { _, action ->
                when (action) {
                    0 -> {
                        val uri = manager.getUriForDownloadedFile(id)
                        val mime = manager.getMimeTypeForDownloadedFile(id) ?: "application/octet-stream"
                        if (uri != null) runCatching { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                            .onFailure { Toast.makeText(this, "No app can open this file", Toast.LENGTH_SHORT).show() }
                    }
                    1 -> {
                        val updated = JSONArray()
                        for (i in 0 until ids.length()) if (ids.optLong(i) != id) updated.put(ids.optLong(i))
                        prefs.edit().putString("download_ids", updated.toString()).apply()
                    }
                    2 -> AlertDialog.Builder(this).setMessage("Delete this downloaded file?")
                        .setPositiveButton("Delete") { _, _ -> manager.remove(id) }.setNegativeButton("Cancel", null).show()
                }
            }.show()
        }.setPositiveButton("Refresh", null).setNegativeButton("Android downloads") { _, _ ->
            runCatching { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
        }.show()
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
        if (requestCode == requestWireGuardConfig) {
            if (resultCode == RESULT_OK && data?.data != null) handleWireGuardProfile(data.data!!)
            return
        }
        if (requestCode == requestWireGuardConsent) {
            if (resultCode == RESULT_OK) {
                val raw = wireGuardStore.load()
                if (!raw.isNullOrBlank()) startWireGuardAfterConsent(raw)
                else Toast.makeText(this, "Import a WireGuard profile first", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this, "VPN permission was not granted", Toast.LENGTH_LONG).show()
            return
        }
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
            "${i + 1}. ${tab.title.ifBlank { tab.url.ifBlank { "New tab" } }}${if (tab.guest) " [Guest]" else if (tab.private) " [Private]" else ""}"
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
                tabs.size + 3 -> duplicateCurrentTab()
                tabs.size + 4 -> current()?.let { togglePinned(it) }
                tabs.size + 5 -> searchTabs()
                tabs.size + 6 -> showRecentlyClosed()
            }
        }.setNegativeButton("Close current") { _, _ -> closeTab() }.show()
    }

    private fun closeOtherTabs() {
        val keep = current() ?: return
        // Pinned tabs survive "Close other tabs", as in mainstream browsers.
        tabs.toList().filter { it !== keep && !it.pinned }.forEach { tab ->
            if (!tab.private && !tab.guest) rememberRecentlyClosed(tab.url)
            tabs.remove(tab)
            tab.view.releaseSession()
            if (tab.session.isOpen) tab.session.close()
        }
        active = tabs.indexOf(keep).coerceAtLeast(0)
        showOnlyActive()
        updateTabs()
        saveTabs()
    }

    private fun rememberRecentlyClosed(url: String) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return
        val recently = runCatching { JSONArray(prefs.getString("recently_closed", "[]")) }.getOrDefault(JSONArray())
        val updated = JSONArray().put(url)
        for (i in 0 until recently.length()) {
            val old = recently.optString(i)
            if (old != url && updated.length() < 20) updated.put(old)
        }
        prefs.edit().putString("recently_closed", updated.toString()).apply()
    }

    private fun duplicateCurrentTab() {
        val source = current() ?: return
        if (source.guest) {
            Toast.makeText(this, "Guest tabs cannot be duplicated into another session.", Toast.LENGTH_SHORT).show()
            return
        }
        val url = source.url.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: "about:blank"
        val oldPrivate = privateMode
        privateMode = source.private
        try {
            newTab(url)
            tabs.lastOrNull()?.group = source.group
            tabs.lastOrNull()?.pinned = false
            saveTabs()
        } finally {
            privateMode = oldPrivate
        }
    }

    private fun closeTab() {
        val removed = current() ?: return
        if (!removed.private && !removed.guest) rememberRecentlyClosed(removed.url)
        tabs.removeAt(active)
        removed.session.setActive(false)
        removed.view.releaseSession()
        removed.session.close()
        if (tabs.isEmpty()) newTab("https://www.google.com")
        else { active = active.coerceAtMost(tabs.lastIndex); showOnlyActive(); updateTabs() }
        saveTabs()
    }

    private fun togglePrivate() {
        if (guestMode) {
            Toast.makeText(this, "End Guest Mode before starting a Private tab.", Toast.LENGTH_LONG).show()
            return
        }
        privateMode = !privateMode
        Toast.makeText(this, if (privateMode) "Private session enabled: Gecko private mode is active." else "Private mode off", Toast.LENGTH_SHORT).show()
        newTab("about:blank")
    }

    private fun showVpnStatus() {
        val configured = wireGuardStore.hasProfile()
        val status = if (wireGuardConnected) "Connected" else if (configured) "Disconnected — WireGuard profile saved" else "Not configured — import a WireGuard profile"
        val message = "$status\n\nNova uses the official WireGuard Android tunnel library. No Nova/AdGuard account is required. You must import a valid .conf profile from a VPN server you control or trust.\n\nImportant: Android's VPN service applies the profile's AllowedIPs routes at device level. This is not guaranteed to protect only Nova Browser. Check the profile's routing and DNS settings before connecting."
        val dialog = AlertDialog.Builder(this).setTitle("Nova VPN").setMessage(message)
            .setNeutralButton("Import .conf", null)
            .setNegativeButton(if (configured) "Remove profile" else "Close", null)
            .setPositiveButton(if (wireGuardConnected) "Disconnect" else "Connect", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { dialog.dismiss(); importWireGuardProfile() }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                if (configured) {
                    if (wireGuardConnected) stopWireGuardTunnel()
                    wireGuardStore.clear()
                    dialog.dismiss()
                    Toast.makeText(this, "WireGuard profile removed", Toast.LENGTH_SHORT).show()
                } else dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.dismiss()
                if (wireGuardConnected) stopWireGuardTunnel() else connectWireGuardTunnel()
            }
        }
        dialog.show()
    }

    private fun importWireGuardProfile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, requestWireGuardConfig)
    }

    private fun handleWireGuardProfile(uri: Uri) {
        try {
            val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalArgumentException("Could not read the selected file")
            require(raw.length <= 65536) { "Configuration file is too large (maximum 64 KB)" }
            Config.parse(BufferedReader(StringReader(raw)))
            wireGuardStore.save(raw)
            Toast.makeText(this, "WireGuard profile imported and encrypted on this device", Toast.LENGTH_LONG).show()
            showVpnStatus()
        } catch (error: Exception) {
            Toast.makeText(this, "Invalid WireGuard profile: ${error.message ?: "check the .conf file"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun connectWireGuardTunnel() {
        val raw = wireGuardStore.load()
        if (raw.isNullOrBlank()) {
            Toast.makeText(this, "Import a WireGuard .conf profile first", Toast.LENGTH_LONG).show()
            importWireGuardProfile()
            return
        }
        val consent = android.net.VpnService.prepare(this)
        if (consent != null) startActivityForResult(consent, requestWireGuardConsent)
        else startWireGuardAfterConsent(raw)
    }

    private fun startWireGuardAfterConsent(raw: String) {
        Toast.makeText(this, "Connecting WireGuard…", Toast.LENGTH_SHORT).show()
        wireGuardExecutor.execute {
            try {
                val config = Config.parse(BufferedReader(StringReader(raw)))
                val state = wireGuardBackend.setState(wireGuardTunnel, Tunnel.State.UP, config)
                wireGuardConnected = state == Tunnel.State.UP
                runOnUiThread {
                    Toast.makeText(this, if (wireGuardConnected) "WireGuard tunnel connected" else "WireGuard did not reach Connected state", Toast.LENGTH_LONG).show()
                    showVpnStatus()
                }
            } catch (error: Exception) {
                wireGuardConnected = false
                runOnUiThread {
                    Toast.makeText(this, "VPN connection failed: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                    showVpnStatus()
                }
            }
        }
    }

    private fun stopWireGuardTunnel() {
        wireGuardExecutor.execute {
            try {
                wireGuardBackend.setState(wireGuardTunnel, Tunnel.State.DOWN, null)
                wireGuardConnected = false
                runOnUiThread {
                    Toast.makeText(this, "WireGuard disconnected", Toast.LENGTH_SHORT).show()
                    showVpnStatus()
                }
            } catch (error: Exception) {
                runOnUiThread { Toast.makeText(this, "Could not disconnect cleanly: ${error.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun closeTabsForRuntimeRestart() {
        saveTabs()
        tabs.toList().forEach { tab ->
            tab.session.setActive(false)
            tab.view.releaseSession()
            if (tab.session.isOpen) tab.session.close()
        }
        tabs.clear()
        active = 0
        if (root.childCount > 2) root.removeViews(2, root.childCount - 2)
    }

    private fun showGuestTorStatus() {
        AlertDialog.Builder(this).setTitle("Tor connection active")
            .setMessage("Nova has started its embedded Tor service and configured GeckoView to use its local SOCKS proxy with remote DNS. WebRTC and HTTP/3 are disabled to reduce common proxy-bypass paths. Use the connection check below to confirm the route. This is Tor-routed GeckoView, not the Tor Browser: its fingerprinting protections and anonymity set are not equivalent. Avoid signing in to personal accounts or sharing identifying information.")
            .setPositiveButton("Check Tor connection") { _, _ ->
                current()?.session?.loadUri("https://check.torproject.org/")
            }
            .setNegativeButton("Close", null).show()
    }

    private fun startGuestMode() {
        if (guestMode) return
        guestReturnTab = current()
        privateMode = false
        closeTabsForRuntimeRestart()
        guestMode = true
        resetRuntime(false, 0)
        refreshGuestModeButton()
        Toast.makeText(this, "Starting Tor… this can take up to two minutes.", Toast.LENGTH_LONG).show()
        torController.start { ready, socksPort, error ->
            if (isFinishing || isDestroyed) return@start
            if (!ready) {
                guestMode = false
                torController.stop()
                resetRuntime(false, 0)
                applyCookiePolicy()
                restoreTabs()
                if (tabs.isEmpty()) newTab(homePage())
                refreshGuestModeButton()
                AlertDialog.Builder(this).setTitle("Tor could not start")
                    .setMessage(error ?: "Nova could not establish a Tor circuit. Guest Mode was not opened, and normal browsing has been restored.")
                    .setPositiveButton("OK", null).show()
                return@start
            }
            resetRuntime(true, socksPort)
            applyCookiePolicy()
            newTab("about:blank")
            Toast.makeText(this, "Tor connected. Guest Mode is active.", Toast.LENGTH_LONG).show()
            showGuestTorStatus()
            refreshGuestModeButton()
        }
    }

    private fun endGuestMode() {
        if (!guestMode) return
        closeTabsForRuntimeRestart()
        guestMode = false
        privateMode = false
        resetRuntime(false, 0)
        torController.stop()
        applyCookiePolicy()
        restoreTabs()
        if (tabs.isEmpty()) newTab(homePage())
        guestReturnTab = null
        refreshGuestModeButton()
        Toast.makeText(this, "Guest session ended. Tor stopped and regular tabs restored.", Toast.LENGTH_LONG).show()
    }

    private fun refreshGuestModeButton() {
        if (!::root.isInitialized || root.childCount < 2) return
        val controls = (root.getChildAt(1) as? HorizontalScrollView)?.getChildAt(0) as? LinearLayout ?: return
        for (i in 0 until controls.childCount) {
            val child = controls.getChildAt(i)
            if (child is Button && (child.text == "Guest Mode" || child.text == "End Guest")) {
                child.text = if (guestMode) "End Guest" else "Guest Mode"
                break
            }
        }
    }

    private fun applyTheme() {
        val bg = if (dark) Color.rgb(30, 30, 30) else Color.WHITE
        root.setBackgroundColor(bg)
        address.setTextColor(if (dark) Color.WHITE else Color.BLACK)
        address.setHintTextColor(if (dark) Color.LTGRAY else Color.GRAY)
    }

    private fun bookmark() {
        if (current()?.guest == true) {
            Toast.makeText(this, "Bookmarks are disabled in Guest Mode.", Toast.LENGTH_SHORT).show()
            return
        }
        val url = current()?.url ?: return
        if (!url.startsWith("https://") && !url.startsWith("http://")) return
        val list = JSONArray(prefs.getString("bookmarks", "[]"))
        if ((0 until list.length()).any { list.optString(it) == url }) return
        list.put(url); prefs.edit().putString("bookmarks", list.toString()).apply()
        Toast.makeText(this, "Bookmark saved", Toast.LENGTH_SHORT).show()
    }

    private fun showSavedList(history: Boolean) {
        if (current()?.guest == true) {
            Toast.makeText(this, "Personal history and bookmarks are hidden in Guest Mode.", Toast.LENGTH_SHORT).show()
            return
        }
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
        if (!guestMode && novaAuth.currentEmail() != null) syncCloud(showMessage = false)
    }

    override fun onPause() {
        current()?.session?.setActive(false)
        saveTabs()
        if (!guestMode && novaAuth.currentEmail() != null) {
            // Best-effort background upload; next launch/resume downloads the latest account snapshot.
            cloudSync.sync { _, _ -> }
        }
        super.onPause()
    }

    override fun onDestroy() {
        tabs.forEach { tab ->
            tab.view.releaseSession()
            if (tab.session.isOpen) tab.session.close()
        }
        tabs.clear()
        if (guestMode) resetRuntime(false, 0)
        torController.stop()
        super.onDestroy()
    }

    companion object {
        @Volatile private var sharedRuntime: GeckoRuntime? = null
        @Volatile private var torProxyEnabled: Boolean = false
        @Volatile private var torSocksPort: Int = 0

        private fun resetRuntime(useTor: Boolean, socksPort: Int) {
            synchronized(this) {
                sharedRuntime?.shutdown()
                sharedRuntime = null
                torProxyEnabled = useTor
                torSocksPort = if (useTor) socksPort else 0
            }
        }

        private fun getOrCreateRuntime(context: Context): GeckoRuntime =
            sharedRuntime ?: synchronized(this) {
                sharedRuntime ?: run {
                    val settings = GeckoRuntimeSettings.Builder()
                    if (torProxyEnabled) {
                        val config = File(context.filesDir, "nova-gecko-tor.yaml")
                        val configText = listOf(
                            "prefs:",
                            "  network.proxy.type.int: 1",
                            "  network.proxy.socks.string: 127.0.0.1",
                            "  network.proxy.socks_port.int: $torSocksPort",
                            "  network.proxy.socks_version.int: 5",
                            "  network.proxy.socks_remote_dns.boolean: true",
                            "  network.proxy.no_proxies_on.string: ''",
                            "  network.proxy.failover_direct.boolean: false",
                            "  network.dns.disablePrefetch.boolean: true",
                            "  network.prefetch-next.boolean: false",
                            "  network.http.speculative-parallel-limit.int: 0",
                            "  network.http.http3.enable.boolean: false",
                            "  media.peerconnection.enabled.boolean: false",
                            "  privacy.resistFingerprinting.boolean: true"
                        ).joinToString("\n")
                        config.writeText(configText)
                        settings.configFilePath(config.absolutePath)
                    } else {
                        // Do not read external debug config in normal or release browsing.
                        settings.configFilePath("")
                    }
                    GeckoRuntime.create(context.applicationContext, settings.build()).also { created ->
                        sharedRuntime = created
                        created.webExtensionController.ensureBuiltIn(
                            "resource://android/assets/nova_adblock/",
                            "nova-adblock@zorvi.browser"
                        ).accept(
                            { android.util.Log.i("NovaAdBlock", "Network request filter installed") },
                            { error -> android.util.Log.e("NovaAdBlock", "Could not install request filter", error) }
                        )
                    }
                }
            }
    }
}
