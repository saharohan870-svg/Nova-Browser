package com.zorvi.browser

import org.json.JSONArray
import org.json.JSONObject

/** Small, privacy-aware persistence model: private tabs are never serialized. */
internal data class SavedBrowserTab(val url: String, val group: String)

internal object BrowserTabState {
    fun encode(tabs: List<SavedBrowserTab>, activeIndex: Int): String {
        val array = JSONArray()
        tabs.forEach { tab ->
            if (tab.url.startsWith("https://") || tab.url.startsWith("http://") || tab.url == "about:blank") {
                array.put(JSONObject().put("url", tab.url).put("group", tab.group))
            }
        }
        return JSONObject()
            .put("tabs", array)
            .put("active", activeIndex.coerceIn(0, (array.length() - 1).coerceAtLeast(0)))
            .toString()
    }

    fun decode(raw: String?): Pair<List<SavedBrowserTab>, Int> {
        if (raw.isNullOrBlank()) return emptyList<SavedBrowserTab>() to 0
        return runCatching {
            val root = JSONObject(raw)
            val array = root.optJSONArray("tabs") ?: JSONArray()
            val result = (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val url = item.optString("url")
                if (!(url.startsWith("https://") || url.startsWith("http://") || url == "about:blank")) null
                else SavedBrowserTab(url, item.optString("group", "Default").take(24).ifBlank { "Default" })
            }
            result to root.optInt("active", 0).coerceIn(0, (result.size - 1).coerceAtLeast(0))
        }.getOrDefault(emptyList<SavedBrowserTab>() to 0)
    }
}
