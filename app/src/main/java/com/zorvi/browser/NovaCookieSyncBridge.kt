package com.zorvi.browser

import android.util.Log
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension

/** User-triggered bridge to Gecko's cookies API. Callers must encrypt exported cookies before cloud upload. */
internal class NovaCookieSyncBridge(private val runtime: GeckoRuntime) {
    @Volatile private var port: WebExtension.Port? = null
    @Volatile private var exportCallback: ((org.json.JSONArray?, String?) -> Unit)? = null
    @Volatile private var importCallback: ((Int, Int, String?) -> Unit)? = null

    fun install(callback: (Boolean, String?) -> Unit) {
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/nova_session_sync/",
            "nova-session-sync@zorvi.browser"
        ).accept({ extension ->
            val delegate = object : WebExtension.MessageDelegate {
                override fun onConnect(port: WebExtension.Port) {
                    this@NovaCookieSyncBridge.port = port
                    port.setDelegate(object : WebExtension.PortDelegate {
                        override fun onPortMessage(message: Any, port: WebExtension.Port) {
                            val json = message as? JSONObject ?: return
                            when (json.optString("type")) {
                                "cookiesExported" -> {
                                    val cb = exportCallback
                                    exportCallback = null
                                    cb?.invoke(json.optJSONArray("cookies") ?: org.json.JSONArray(), null)
                                }
                                "cookiesImported" -> {
                                    val cb = importCallback
                                    importCallback = null
                                    cb?.invoke(json.optInt("imported"), json.optInt("skipped"), null)
                                }
                                "cookieSyncError" -> {
                                    val error = json.optString("message", "Cookie transfer failed.")
                                    exportCallback?.invoke(null, error)
                                    exportCallback = null
                                    importCallback?.invoke(0, 0, error)
                                    importCallback = null
                                }
                            }
                        }
                        override fun onDisconnect(port: WebExtension.Port) {
                            if (this@NovaCookieSyncBridge.port === port) this@NovaCookieSyncBridge.port = null
                        }
                    })
                }
            }
            extension.setMessageDelegate(delegate, "browser")
            callback(true, null)
        }, { error ->
            Log.e("NovaCookieSync", "Could not install session sync extension", error)
            callback(false, error.message ?: "Could not install session sync extension.")
        })
    }

    fun exportCookies(callback: (org.json.JSONArray?, String?) -> Unit) {
        val activePort = port
        if (activePort == null) {
            callback(null, "Session sync extension is not connected yet. Reopen Nova and try again.")
            return
        }
        exportCallback = callback
        activePort.postMessage(JSONObject().put("type", "exportCookies"))
    }

    fun importCookies(cookies: org.json.JSONArray, callback: (Int, Int, String?) -> Unit) {
        val activePort = port
        if (activePort == null) {
            callback(0, 0, "Session sync extension is not connected yet. Reopen Nova and try again.")
            return
        }
        importCallback = callback
        activePort.postMessage(JSONObject().put("type", "importCookies").put("cookies", cookies))
    }
}
