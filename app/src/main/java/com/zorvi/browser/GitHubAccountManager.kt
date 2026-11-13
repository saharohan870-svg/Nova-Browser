package com.zorvi.browser

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

/**
 * Optional GitHub account foundation. Tokens are encrypted with an Android Keystore AES key.
 * No token is requested or stored until the user explicitly starts device authorization.
 */
class GitHubAccountManager(context: Context) {
    private val prefs = context.getSharedPreferences("zorvi_github_account", Context.MODE_PRIVATE)
    private val alias = "zorvi_github_token_key"
    @Volatile private var pollingIntervalSeconds = 5

    fun nextPollIntervalSeconds(): Int = pollingIntervalSeconds.coerceAtLeast(5)

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val intervalSeconds: Int,
        val expiresInSeconds: Int
    )

    fun isConnected(): Boolean = !readToken().isNullOrBlank()

    fun requestDeviceCode(clientId: String): DeviceCode {
        require(clientId.isNotBlank()) { "GitHub OAuth client ID is required" }
        val body = "client_id=" + enc(clientId) + "&scope=" + enc("read:user codespace")
        val json = postForm("https://github.com/login/device/code", body)
        val verificationUri = json.optString("verification_uri", "https://github.com/login/device")
        val parsedUri = URL(verificationUri)
        require(parsedUri.protocol == "https" && parsedUri.host == "github.com") {
            "GitHub returned an unexpected authorization URL"
        }
        pollingIntervalSeconds = json.optInt("interval", 5).coerceAtLeast(5)
        return DeviceCode(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUri = verificationUri,
            intervalSeconds = pollingIntervalSeconds,
            expiresInSeconds = json.optInt("expires_in", 900)
        )
    }

    /** Returns an access token when approved, null while authorization is pending. */
    fun pollForToken(clientId: String, deviceCode: String): String? {
        val body = "client_id=" + enc(clientId) + "&device_code=" + enc(deviceCode) +
            "&grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code")
        val connection = URL("https://github.com/login/oauth/access_token").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.doOutput = true
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val json = JSONObject(stream.bufferedReader().use { it.readText() })
        connection.disconnect()
        val token = json.optString("access_token").takeIf { it.isNotBlank() }
        if (token != null) {
            getJson("https://api.github.com/user", token)
            saveToken(token)
            return token
        }
        val error = json.optString("error")
        if (error == "authorization_pending") return null
        if (error == "slow_down") {
            pollingIntervalSeconds += 5
            return null
        }
        throw IllegalStateException(json.optString("error_description", error.ifBlank { "GitHub authorization failed" }))
    }

    fun disconnect() {
        prefs.edit().remove("token_iv").remove("token_ciphertext").commit()
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }

    fun accountSummary(): String {
        val token = readToken() ?: throw IllegalStateException("GitHub is not connected")
        val json = getJson("https://api.github.com/user", token)
        val login = json.optString("login", "GitHub user")
        val name = json.optString("name").takeIf { it.isNotBlank() }
        return if (name == null) "@$login" else "$name (@$login)"
    }

    fun listCodespaces(): List<String> {
        val token = readToken() ?: throw IllegalStateException("Connect GitHub first")
        val json = getJson("https://api.github.com/user/codespaces?per_page=30", token)
        val items = json.optJSONArray("codespaces") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name")
            val state = item.optString("state")
            if (name.isBlank()) null else "$name — $state"
        }
    }

    private fun saveToken(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("token_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("token_ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    private fun readToken(): String? = runCatching {
        val iv = Base64.decode(prefs.getString("token_iv", null), Base64.NO_WRAP)
        val encrypted = Base64.decode(prefs.getString("token_ciphertext", null), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun postForm(url: String, body: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.doOutput = true
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val json = JSONObject(stream.bufferedReader().use { it.readText() })
        connection.disconnect()
        return json
    }

    private fun getJson(url: String, token: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("Authorization", "Bearer $token")
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream.bufferedReader().use { it.readText() }
        connection.disconnect()
        if (code !in 200..299) throw IllegalStateException("GitHub API returned HTTP $code")
        return JSONObject(text)
    }

    private fun enc(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
}
