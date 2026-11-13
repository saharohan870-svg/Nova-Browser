package com.zorvi.browser

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Stores only an AES-GCM encrypted envelope in Supabase.
 * Plaintext and the sync passphrase stay on the Android client.
 */
internal class NovaEncryptedVaultSync(private val auth: NovaSupabaseAuth) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun backup(plaintextVaultJson: String, passphrase: CharArray, callback: (Boolean, String) -> Unit) {
        auth.withFreshAccessToken { token ->
            if (token.isNullOrBlank()) {
                passphrase.fill('\u0000')
                callback(false, "Sign in to Nova before backing up the encrypted vault.")
                return@withFreshAccessToken
            }
            executor.execute {
                try {
                    val envelope = NovaVaultCrypto.encrypt(plaintextVaultJson, passphrase)
                    request(
                        "/rest/v1/nova_encrypted_vault?on_conflict=user_id",
                        token,
                        JSONObject().put("payload", envelope),
                        "POST"
                    )
                    main.post { callback(true, "Encrypted vault backup updated.") }
                } catch (error: Exception) {
                    main.post { callback(false, error.message ?: "Encrypted vault backup failed.") }
                } finally {
                    passphrase.fill('\u0000')
                }
            }
        }
    }

    fun restore(passphrase: CharArray, callback: (String?, String) -> Unit) {
        auth.withFreshAccessToken { token ->
            if (token.isNullOrBlank()) {
                passphrase.fill('\u0000')
                callback(null, "Sign in to Nova before restoring the encrypted vault.")
                return@withFreshAccessToken
            }
            executor.execute {
                try {
                    val rows = request(
                        "/rest/v1/nova_encrypted_vault?select=payload&limit=1",
                        token,
                        null,
                        "GET"
                    ) as? JSONArray ?: JSONArray()
                    if (rows.length() == 0) {
                        main.post { callback(null, "No encrypted vault backup exists for this account yet.") }
                    } else {
                        val envelope = rows.getJSONObject(0).getJSONObject("payload")
                        val plaintext = NovaVaultCrypto.decrypt(envelope, passphrase)
                        main.post { callback(plaintext, "Encrypted vault restored on this device.") }
                    }
                } catch (error: javax.crypto.AEADBadTagException) {
                    main.post { callback(null, "That sync passphrase is incorrect, or the encrypted backup was changed.") }
                } catch (error: Exception) {
                    main.post { callback(null, error.message ?: "Encrypted vault restore failed.") }
                } finally {
                    passphrase.fill('\u0000')
                }
            }
        }
    }

    private fun request(path: String, token: String, body: JSONObject?, method: String): Any {
        val connection = (URL(NovaSupabaseConfig.PROJECT_URL.trimEnd('/') + path)
            .openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("apikey", NovaSupabaseConfig.PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (method == "POST") {
                setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
                doOutput = true
            }
        }
        try {
            if (body != null) connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(raw).optString("message") }.getOrDefault("")
                throw IllegalStateException(detail.ifBlank { "Encrypted vault request returned HTTP $status." })
            }
            return if (raw.isBlank()) JSONObject()
            else if (raw.trimStart().startsWith("[")) JSONArray(raw) else JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }
}
