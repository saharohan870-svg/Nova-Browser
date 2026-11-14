package com.zorvi.browser

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/** Device-local credential store. Each record is encrypted with a non-exportable Android Keystore key. */
internal class NovaLoginStore(context: Context) {
    private val prefs = context.getSharedPreferences("nova_login_store", Context.MODE_PRIVATE)
    private val alias = "nova.login.store.v1"
    private val lock = Any()

    fun fetch(domain: String? = null): Array<Autocomplete.LoginEntry> = synchronized(lock) {
        val entries = read()
        entries.filter {
            if (domain == null) true
            else {
                val host = runCatching { java.net.URI(it.optString("origin")).host }.getOrNull()
                host.equals(domain, ignoreCase = true) ||
                    it.optString("origin").equals(domain, ignoreCase = true)
            }
        }.map { toEntry(it) }.toTypedArray()
    }

    fun save(login: Autocomplete.LoginEntry) = synchronized(lock) {
        val entries = read()
        val guid = login.guid?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
        val record = JSONObject()
            .put("guid", guid)
            .put("origin", login.origin)
            .put("formActionOrigin", login.formActionOrigin ?: "")
            .put("httpRealm", login.httpRealm ?: "")
            .put("username", login.username)
            .put("password", login.password)
        val updated = JSONArray()
        var replaced = false
        for (i in 0 until entries.length()) {
            val old = entries.optJSONObject(i) ?: continue
            val same = old.optString("origin") == login.origin &&
                old.optString("username") == login.username
            if (same) {
                if (!replaced) updated.put(record)
                replaced = true
            } else updated.put(old)
        }
        if (!replaced) updated.put(record)
        write(updated)
    }

    fun remove(guid: String) = synchronized(lock) {
        val entries = read()
        val updated = JSONArray()
        for (i in 0 until entries.length()) {
            val item = entries.optJSONObject(i) ?: continue
            if (item.optString("guid") != guid) updated.put(item)
        }
        write(updated)
    }

    fun exportEntriesForEncryptedSync(): JSONArray = synchronized(lock) {
        JSONArray(read().toString())
    }

    fun importFromEncryptedSync(json: String) = synchronized(lock) {
        val root = JSONObject(json)
        val incoming = root.optJSONArray("websiteLogins")
            ?: root.optJSONArray("entries") ?: JSONArray()
        val validated = JSONArray()
        for (i in 0 until incoming.length()) {
            val item = incoming.optJSONObject(i) ?: continue
            val origin = item.optString("origin").ifBlank { item.optString("site") }
            if (!origin.startsWith("https://") || item.optString("password").isBlank()) continue
            validated.put(JSONObject(item.toString()).put("origin", origin))
        }
        write(validated)
    }

    private fun toEntry(item: JSONObject): Autocomplete.LoginEntry =
        Autocomplete.LoginEntry.Builder()
            .guid(item.optString("guid"))
            .origin(item.optString("origin"))
            .formActionOrigin(item.optString("formActionOrigin").takeIf { it.isNotBlank() })
            .httpRealm(item.optString("httpRealm").takeIf { it.isNotBlank() })
            .username(item.optString("username"))
            .password(item.optString("password"))
            .build()

    private fun read(): JSONArray {
        val encrypted = prefs.getString("credentials_enc", null) ?: return JSONArray()
        return try {
            val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, 12)
            val ciphertext = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            JSONArray(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun write(entries: JSONArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(entries.toString().toByteArray(Charsets.UTF_8))
        prefs.edit().putString("credentials_enc", Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
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
