package com.zorvi.browser

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Per-account snapshot sync. RLS scopes every request to auth.uid(). */
internal class NovaCloudSync(
    private val preferences: SharedPreferences,
    private val auth: NovaSupabaseAuth
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val syncedKeys = listOf(
        "bookmarks", "history", "open_tabs", "active_tab", "recently_closed",
        "engine", "dark_mode", "home_page", "reader_text_size", "reader_dark",
        "translation_language", "tracking_protection", "cookie_policy",
        "desktop_mode", "active_group", "direct_downloads"
    )

    fun sync(callback: (Boolean, String) -> Unit) {
        if (!running.compareAndSet(false, true)) {
            callback(false, "A sync is already running.")
            return
        }
        auth.withFreshAccessToken { token ->
            if (token.isNullOrBlank()) {
                running.set(false)
                callback(false, "Sign in to Nova to sync your browser.")
                return@withFreshAccessToken
            }
            executor.execute {
                try {
                    val local = readLocalSnapshot()
                    val rows = request("/rest/v1/nova_sync_state?select=snapshot,updated_at&limit=1", token, null, "GET") as? JSONArray
                    val remote = if (rows != null && rows.length() > 0) rows.optJSONObject(0)?.optJSONObject("snapshot") else null
                    val merged = if (remote == null) local else mergeSnapshots(remote, local)
                    request("/rest/v1/nova_sync_state?on_conflict=user_id", token,
                        JSONObject().put("snapshot", merged), "POST")
                    applySnapshot(merged)
                    preferences.edit().putBoolean("cloud_sync_initialized", true)
                        .putLong("cloud_sync_last_success", System.currentTimeMillis()).apply()
                    main.post { callback(true, "Nova is synced across your devices.") }
                } catch (error: Exception) {
                    main.post { callback(false, error.message ?: "Cloud sync failed.") }
                } finally {
                    running.set(false)
                }
            }
        }
    }

    private fun readLocalSnapshot(): JSONObject {
        val result = JSONObject()
        for (key in syncedKeys) {
            val value = preferences.all[key] ?: continue
            when (value) {
                is String, is Boolean, is Int, is Long -> result.put(key, value)
                is Float -> result.put(key, value.toDouble())
            }
        }
        for ((key, value) in preferences.all) {
            if (!key.startsWith("site_permission_")) continue
            when (value) {
                is String, is Boolean, is Int, is Long -> result.put(key, value)
            }
        }
        // Android DownloadManager IDs, auth tokens, and private/guest data are never uploaded.
        return result
    }

    private fun mergeSnapshots(remote: JSONObject, local: JSONObject): JSONObject {
        val merged = JSONObject(remote.toString())
        val arrayKeys = setOf("bookmarks", "history", "recently_closed", "open_tabs")
        for (key in local.keys()) {
            val localValue = local.opt(key)
            if (key in arrayKeys) {
                val combined = JSONArray()
                val seen = LinkedHashSet<String>()
                fun append(array: JSONArray) {
                    for (i in 0 until array.length()) {
                        val item = array.opt(i)
                        val identity = if (key == "open_tabs" && item is JSONObject) item.optString("url") else item.toString()
                        if (identity.isNotBlank() && seen.add(identity)) combined.put(item)
                    }
                }
                append(parseArray(merged.opt(key)))
                append(parseArray(localValue))
                val limit = when (key) { "history" -> 300; "recently_closed" -> 20; "open_tabs" -> 100; else -> 1000 }
                val trimmed = JSONArray()
                for (i in (combined.length() - limit).coerceAtLeast(0) until combined.length()) trimmed.put(combined.opt(i))
                merged.put(key, trimmed)
            } else if (!merged.has(key)) merged.put(key, localValue)
        }
        return merged
    }

    private fun parseArray(value: Any?): JSONArray = when (value) {
        is JSONArray -> value
        is String -> runCatching { JSONArray(value) }.getOrDefault(JSONArray())
        else -> JSONArray()
    }

    private fun applySnapshot(snapshot: JSONObject) {
        val editor = preferences.edit()
        for (key in snapshot.keys()) {
            when (val value = snapshot.opt(key)) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Number -> editor.putInt(key, value.toInt())
                is JSONArray, is JSONObject -> editor.putString(key, value.toString())
            }
        }
        editor.apply()
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
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(raw).optString("message") }.getOrDefault("")
                throw IllegalStateException(detail.ifBlank { "Cloud sync returned HTTP $status." })
            }
            return if (raw.isBlank()) JSONObject() else if (raw.trimStart().startsWith("[")) JSONArray(raw) else JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }
}
