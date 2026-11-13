package com.zorvi.browser

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import android.util.Base64

/** Google identity sign-in. Only verified profile fields are retained; the ID token is never persisted. */
class GoogleAccountManager(context: Context) {
    private val prefs = context.getSharedPreferences("zorvi_google_account", Context.MODE_PRIVATE)
    private val credentialManager = CredentialManager.create(context)

    data class Profile(val subject: String, val email: String, val name: String)

    fun profile(): Profile? {
        val subject = prefs.getString("subject", null) ?: return null
        return Profile(subject, prefs.getString("email", "").orEmpty(), prefs.getString("name", "").orEmpty())
    }

    suspend fun signIn(activity: Activity, serverClientId: String): Profile {
        require(serverClientId.isNotBlank()) { "Google Web OAuth client ID is required" }
        val nonceBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val nonce = Base64.encodeToString(nonceBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = credentialManager.getCredential(activity, request)
        val credential = response.credential
        require(credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google did not return a valid ID-token credential"
        }
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val claims = withContext(Dispatchers.IO) { verifyIdToken(googleCredential.idToken, serverClientId, nonce) }
        val profile = Profile(
            claims.getString("sub"),
            claims.getString("email"),
            claims.optString("name").ifBlank { claims.getString("email") }
        )
        prefs.edit().putString("subject", profile.subject)
            .putString("email", profile.email).putString("name", profile.name).apply()
        return profile
    }

    suspend fun disconnect() {
        prefs.edit().clear().apply()
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }

    private fun verifyIdToken(idToken: String, expectedAudience: String, expectedNonce: String): JSONObject {
        val url = URL("https://oauth2.googleapis.com/tokeninfo?id_token=" + URLEncoder.encode(idToken, "UTF-8"))
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        connection.disconnect()
        require(code in 200..299) { "Google rejected the sign-in token (HTTP $code)" }
        val claims = JSONObject(body)
        require(claims.optString("iss") in setOf("https://accounts.google.com", "accounts.google.com")) { "Google token issuer was invalid" }
        require(claims.optString("aud") == expectedAudience) { "Google token audience did not match this app" }
        val expiresAt = claims.optLong("exp", 0L)
        require(expiresAt > System.currentTimeMillis() / 1000L) { "Google sign-in token has expired" }
        require(claims.optString("email_verified") == "true") { "Google account email is not verified" }
        require(claims.optString("sub").isNotBlank()) { "Google account identifier is missing" }
        require(claims.optString("nonce") == expectedNonce) { "Google sign-in nonce did not match" }
        return claims
    }
}
