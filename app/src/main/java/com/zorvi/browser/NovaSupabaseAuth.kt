package com.zorvi.browser

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.concurrent.Executors

/**
 * Supabase Auth REST client. Only the public publishable key is shipped in the APK.
 * Session tokens are encrypted with an Android Keystore AES-GCM key.
 */
internal class NovaSupabaseAuth(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("nova_supabase_auth", Context.MODE_PRIVATE)
    private val sessionStore = SecureSessionStore(prefs)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    val isConfigured: Boolean
        get() = NovaSupabaseConfig.PROJECT_URL.startsWith("https://") &&
            NovaSupabaseConfig.PUBLISHABLE_KEY.startsWith("sb_publishable_")

    fun currentEmail(): String? = sessionStore.read()?.optString("email")?.takeIf { it.isNotBlank() }

    /** Refreshes a near-expiry session on app startup, retaining sign-in across launches. */
    fun restoreSession(callback: (Boolean) -> Unit) {
        executor.execute {
            val restored = runCatching {
                val session = sessionStore.read() ?: return@runCatching false
                ensureFreshSession(session)
                true
            }.getOrDefault(false)
            if (!restored) sessionStore.clear()
            mainHandler.post { callback(restored) }
        }
    }

    fun signIn(email: String, password: String, callback: (Boolean, String?) -> Unit) =
        runAuth("/auth/v1/token?grant_type=password",
            JSONObject().put("email", email).put("password", password), callback, true)

    fun signUp(email: String, password: String, callback: (Boolean, String?) -> Unit) =
        runAuth("/auth/v1/signup",
            JSONObject().put("email", email).put("password", password), callback, false)

    /** Passwordless email OTP. Supabase may create the user if it does not exist. */
    fun requestOtp(email: String, callback: (Boolean, String?) -> Unit) =
        runAuth("/auth/v1/otp",
            JSONObject().put("email", email).put("create_user", true), callback, false)

    fun verifyOtp(email: String, token: String, callback: (Boolean, String?) -> Unit) =
        runAuth("/auth/v1/verify",
            JSONObject().put("email", email).put("token", token).put("type", "email"), callback, true)

    /** Sends the password-recovery email. The Supabase recovery template must contain {{ .Token }} for code entry. */
    fun resetPassword(email: String, callback: (Boolean, String?) -> Unit) =
        runAuth("/auth/v1/recover", JSONObject().put("email", email), callback, false)

    fun verifyRecoveryOtp(email: String, token: String, callback: (Boolean, String?) -> Unit) =
        runAuth("/auth/v1/verify",
            JSONObject().put("email", email).put("token", token).put("type", "recovery"), callback, true)

    fun updatePassword(newPassword: String, callback: (Boolean, String?) -> Unit) {
        if (newPassword.length < 8) {
            callback(false, "Use a password with at least 8 characters.")
            return
        }
        executor.execute {
            try {
                val session = ensureFreshSession(sessionStore.read()
                    ?: throw IllegalStateException("Your recovery session expired. Request a new reset code."))
                request("/auth/v1/user", JSONObject().put("password", newPassword),
                    session.optString("access_token"), "PUT")
                mainHandler.post { callback(true, "Your Nova password has been updated.") }
            } catch (e: Exception) {
                mainHandler.post { callback(false, e.message ?: "Could not update password.") }
            }
        }
    }

    fun signOut(callback: () -> Unit) {
        executor.execute {
            runCatching {
                val session = sessionStore.read()
                val token = session?.optString("access_token")
                if (!token.isNullOrBlank() && isConfigured) {
                    request("/auth/v1/logout", JSONObject(), token)
                }
            }
            sessionStore.clear()
            mainHandler.post { callback() }
        }
    }

    /** Supplies a refreshed user access token to cloud features; never exposes the refresh token. */
    fun withFreshAccessToken(callback: (String?) -> Unit) {
        if (!isConfigured) {
            mainHandler.post { callback(null) }
            return
        }
        executor.execute {
            val token = runCatching {
                val session = sessionStore.read()
                    ?: throw IllegalStateException("Sign in to your Nova account first.")
                ensureFreshSession(session).optString("access_token").takeIf { it.isNotBlank() }
            }.getOrNull()
            mainHandler.post { callback(token) }
        }
    }

    private fun runAuth(
        path: String,
        body: JSONObject,
        callback: (Boolean, String?) -> Unit,
        saveSession: Boolean
    ) {
        if (!isConfigured) {
            callback(false, "Supabase is not configured. Check the project URL and publishable key.")
            return
        }
        executor.execute {
            try {
                val result = request(path, body, null)
                if (saveSession) {
                    if (result.optString("access_token").isBlank() ||
                        result.optJSONObject("user") == null) {
                        throw IllegalStateException("Supabase did not return a valid session.")
                    }
                    sessionStore.write(result)
                }
                val message = when {
                    path.endsWith("/signup") ->
                        "Account created. Check your inbox for the confirmation email, then sign in."
                    path.endsWith("/otp") ->
                        "If this email can receive a code, a six-digit OTP has been sent."
                    path.endsWith("/recover") ->
                        "If an account exists, a password-reset code has been sent."
                    else -> null
                }
                mainHandler.post { callback(true, message) }
            } catch (e: Exception) {
                mainHandler.post { callback(false, e.message ?: "Supabase request failed.") }
            }
        }
    }

    /** Must only be called from the auth executor. */
    private fun ensureFreshSession(existing: JSONObject): JSONObject {
        val expiresAt = existing.optLong("expires_at", 0L)
        if (expiresAt > System.currentTimeMillis() / 1000L + 60L) return existing
        val refreshToken = existing.optString("refresh_token")
        if (refreshToken.isBlank()) throw IllegalStateException("Your session expired. Please sign in again.")
        val refreshed = request("/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", refreshToken), null)
        if (refreshed.optString("access_token").isBlank()) {
            throw IllegalStateException("Could not refresh your session. Please sign in again.")
        }
        sessionStore.write(refreshed)
        return refreshed
    }

    private fun request(path: String, body: JSONObject, bearer: String?, method: String = "POST"): JSONObject {
        val connection = (URL(NovaSupabaseConfig.PROJECT_URL.trimEnd('/') + path)
            .openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 15000
            // Publishable API keys are not JWTs: send them only as apikey.
            // Authorization is reserved for a real Supabase user access token.
            setRequestProperty("apikey", NovaSupabaseConfig.PUBLISHABLE_KEY)
            if (!bearer.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearer")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (raw.isBlank()) JSONObject() else JSONObject(raw)
            if (status !in 200..299) {
                val detail = json.optString("msg").ifBlank { json.optString("message") }
                    .ifBlank { json.optString("error_description") }
                    .ifBlank { json.optString("error") }
                throw IllegalStateException(detail.ifBlank { "Supabase returned HTTP $status." })
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Encrypts the complete Supabase session at rest using a non-exportable Android Keystore key.
     * If a previous plaintext session exists from an older Nova build, discard it rather than
     * migrating bearer tokens without a secure storage boundary.
     */
    private class SecureSessionStore(private val prefs: SharedPreferences) {
        private val alias = "nova.supabase.session.v1"

        init {
            // Remove legacy plaintext session fields from pre-secure builds.
            prefs.edit().remove("access_token").remove("refresh_token").remove("email").apply()
        }

        @Synchronized fun read(): JSONObject? {
            val encrypted = prefs.getString("session_enc", null) ?: return null
            return try {
                val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
                val iv = bytes.copyOfRange(0, 12)
                val ciphertext = bytes.copyOfRange(12, bytes.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
                JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
            } catch (_: Exception) {
                clear()
                null
            }
        }

        @Synchronized fun write(session: JSONObject) {
            val copy = JSONObject(session.toString())
            val expiresIn = copy.optLong("expires_in", 3600L)
            copy.put("expires_at", System.currentTimeMillis() / 1000L + expiresIn)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            val encrypted = cipher.iv + cipher.doFinal(copy.toString().toByteArray(Charsets.UTF_8))
            prefs.edit().putString("session_enc", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .remove("access_token").remove("refresh_token").remove("email").apply()
        }

        @Synchronized fun clear() {
            prefs.edit().clear().apply()
        }

        private fun getKey(): SecretKey {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = keyStore.getKey(alias, null) as? SecretKey
            if (existing != null) return existing
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
            return generator.generateKey()
        }
    }
}
