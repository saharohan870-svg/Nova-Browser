package com.zorvi.browser

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.util.concurrent.Executors

internal data class DownloadEntry(
    val name: String,
    val uri: String,
    val source: String,
    val status: String
)

/** Saves GeckoView's external response stream to Android Downloads and persists its index. */
internal class BrowserDownloadManager(
    private val context: Context,
    private val changed: () -> Unit
) {
    private val prefs = context.getSharedPreferences("zorvi_downloads", Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadExecutor()

    fun entries(): List<DownloadEntry> {
        val json = JSONArray(prefs.getString("entries", "[]"))
        return (0 until json.length()).mapNotNull { i ->
            val item = json.optJSONObject(i) ?: return@mapNotNull null
            DownloadEntry(
                item.optString("name"),
                item.optString("uri"),
                item.optString("source"),
                item.optString("status", "Complete")
            )
        }.reversed()
    }

    fun start(response: WebResponse) {
        val body = response.body
        if (body == null) {
            changed()
            return
        }
        val name = safeName(filename(response), response.uri)
        val source = response.uri
        val record = DownloadEntry(name, "", source, "Downloading")
        save(record)
        changed()
        worker.execute {
            var destination: Uri? = null
            var legacyFile: File? = null
            try {
                val mime = response.headers.entries.firstOrNull {
                    it.key.equals("Content-Type", true)
                }?.value?.substringBefore(";")?.trim()?.takeIf { it.contains("/") }
                    ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                    ?: "application/octet-stream"

                val output = if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, mime)
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Zorvi Browser")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    destination = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("Android could not create the download file")
                    context.contentResolver.openOutputStream(destination!!, "w")
                        ?: error("Android could not open the download file")
                } else {
                    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Zorvi Browser")
                    if (!dir.exists() && !dir.mkdirs()) error("Could not create download folder")
                    legacyFile = uniqueFile(dir, name)
                    FileOutputStream(legacyFile!!)
                }

                output.use { out ->
                    body.use { input -> input.copyTo(out, DEFAULT_BUFFER_SIZE) }
                }

                if (Build.VERSION.SDK_INT >= 29 && destination != null) {
                    val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                    context.contentResolver.update(destination!!, done, null, null)
                }
                val savedUri = destination?.toString() ?: legacyFile!!.absolutePath
                save(DownloadEntry(name, savedUri, source, "Complete"))
            } catch (error: Exception) {
                runCatching { body.close() }
                if (destination != null) runCatching { context.contentResolver.delete(destination!!, null, null) }
                if (legacyFile != null) runCatching { legacyFile!!.delete() }
                save(DownloadEntry(name, "", source, "Failed: " + (error.message ?: "download error").take(80)))
            }
            changed()
        }
    }

    fun clearCompleted() {
        val kept = entries().filter { it.status != "Complete" }
        val json = JSONArray()
        kept.reversed().forEach { item ->
            json.put(JSONObject().put("name", item.name).put("uri", item.uri)
                .put("source", item.source).put("status", item.status))
        }
        prefs.edit().putString("entries", json.toString()).apply()
        changed()
    }

    private fun save(entry: DownloadEntry) {
        val current = entries().filterNot { it.name == entry.name && it.source == entry.source }
        val all = (current + entry).takeLast(50)
        val json = JSONArray()
        all.forEach { item ->
            json.put(JSONObject().put("name", item.name).put("uri", item.uri)
                .put("source", item.source).put("status", item.status))
        }
        prefs.edit().putString("entries", json.toString()).apply()
    }

    private fun filename(response: WebResponse): String {
        val disposition = response.headers.entries.firstOrNull {
            it.key.equals("Content-Disposition", true)
        }?.value.orEmpty()
        val encoded = Regex("""filename\*=UTF-8''([^;]+)""", RegexOption.IGNORE_CASE)
            .find(disposition)?.groupValues?.getOrNull(1)
        if (!encoded.isNullOrBlank()) return runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(encoded)
        val plain = Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE)
            .find(disposition)?.groupValues?.getOrNull(1)
        if (!plain.isNullOrBlank()) return plain
        return runCatching { response.uri.substringBefore('?').substringAfterLast('/') }
            .getOrDefault("").ifBlank { "download" }
    }

    private fun safeName(raw: String, source: String): String {
        val clean = raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim().take(120)
        return clean.ifBlank { "download-" + source.hashCode().toUInt().toString(16) }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        var index = 1
        while (candidate.exists()) {
            val base = name.substringBeforeLast('.', name)
            val ext = name.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
            candidate = File(dir, "$base ($index)$ext")
            index++
        }
        return candidate
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}
