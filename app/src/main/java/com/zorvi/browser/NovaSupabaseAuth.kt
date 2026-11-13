package com.zorvi.browser

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Supabase Auth REST client. Uses only the project URL and publishable/anon key.
 * Never place a service_role key in the Android app.
 */
internal class NovaSupabaseAuth(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nova_supabase_auth", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    val isConfigured: Boolean
        get() = NovaSupabaseConfig.PROJECT_URL.startsWith("https://") &&
            NovaSupabaseConfig.ANON_KEY.isNotBlank()

    fun currentEmail(): String? = prefs.getString("email", null)
        ?.takeIf { !prefs.getString("access_token", null).isNullOrBlank() }

    fun signIn(email: String, password: String, callback: (Boolean, String?) -> Unit) =
        runAuth("/auth/v1/token?grant_type=password", JSONObject().put("email", email).put("password", password), callback, true)

    fun signUp(email: String, password: String, callback: (Boolean, String?) -> Unit) =
        runAuth("/auth/v1/signup", JSONObject().put("email", email).put("password", password), callback, false)

    fun requestOtp(email: String, callback: (Boolean, String?) -> Unit) =
        runAuth("/auth/v1/otp", JSONObject().put("email", email).put("create_user", true), callback, false)

    fun verifyOtp(email: String, token: String, callback: (Boolean, String?) -> Unit) =
        runAuth("/auth/v1/verify", JSONObject().put("email", email).put("token", token).put("type", "email"), callback, true)

    fun resetPassword(email: String, callback: (Boolean, String?) -> Unit) =
        runAuth("/auth/v1/recover", JSONObject().put("email", email), callback, false)

    fun signOut(callback: () -> Unit) {
        executor.execute {
            val token = prefs.getString("access_token", null)
            if (!token.isNullOrBlank() && isConfigured) {
                runCatching { request("/auth/v1/logout", JSONObject(), token) }
            }
            prefs.edit().clear().apply()
            callback()
        }
    }

    private fun runAuth(path: String, body: JSONObject, callback: (Boolean, String?) -> Unit, saveSession: Boolean) {
        if (!isConfigured) { callback(false, "Supabase is not configured. Add the project URL and anon key."); return }
        executor.execute {
            try {
                val result = request(path, body, null)
                if (saveSession) {
                    val access = result.optString("access_token")
                    val user = result.optJSONObject("user")
                    if (access.isBlank() || user == null) throw IllegalStateException("Supabase did not return a valid session.")
                    prefs.edit().putString("access_token", access)
                        .putString("refresh_token", result.optString("refresh_token"))
                        .putString("email", user.optString("email")).apply()
                    callback(true, null)
                } else {
                    val message = if (path.endsWith("/signup"))
                        "Account created. Check your inbox for the confirmation email, then sign in."
                    else if (path.endsWith("/otp"))
                        "If this email can receive a code, a six-digit OTP has been sent."
                    else if (path.endsWith("/recover"))
                        "If an account exists, a password-reset email has been sent."
                    else "Request completed."
                    callback(true, message)
                }
            } catch (e: Exception) {
                callback(false, e.message ?: "Supabase request failed.")
            }
        }
    }

    private fun request(path: String, body: JSONObject, bearer: String?): JSONObject {
        val url = URL(NovaSupabaseConfig.PROJECT_URL.trimEnd('/') + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("apikey", NovaSupabaseConfig.ANON_KEY)
            setRequestProperty("Authorization", "Bearer " + (bearer ?: NovaSupabaseConfig.ANON_KEY))
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (raw.isBlank()) JSONObject() else JSONObject(raw)
            if (status !in 200..299) {
                val detail = json.optString("msg").ifBlank { json.optString("message") }
                    .ifBlank { json.optString("error_description") }.ifBlank { json.optString("error") }
                throw IllegalStateException(detail.ifBlank { "Supabase returned HTTP $status." })
            }
            return json
        } finally {
            connection.disconnect()
        }
    }
}
