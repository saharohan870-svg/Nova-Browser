package com.zorvi.browser

import android.util.Log
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

/** Native UI bridge for the bundled EasyList/EasyPrivacy Shields extension. */
internal class NovaShieldsBridge(private val runtime: GeckoRuntime) {
    @Volatile private var port: WebExtension.Port? = null
    @Volatile private var stateCallback: ((JSONObject) -> Unit)? = null

    fun install(callback: (Boolean, String?) -> Unit) {
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/nova_adblock/",
            "nova-adblock@zorvi.browser"
        ).accept({ extension ->
            if (extension == null) {
                callback(false, "Extension installation returned no extension.")
                return@accept
            }
            val delegate = object : WebExtension.MessageDelegate {
                override fun onConnect(port: WebExtension.Port) {
                    this@NovaShieldsBridge.port = port
                    port.setDelegate(object : WebExtension.PortDelegate {
                        override fun onPortMessage(message: Any, port: WebExtension.Port) {
                            val json = message as? JSONObject ?: return
                            if (json.optString("type") == "shieldState") {
                                stateCallback?.invoke(json)
                            }
                        }
                        override fun onDisconnect(port: WebExtension.Port) {
                            if (this@NovaShieldsBridge.port === port) this@NovaShieldsBridge.port = null
                        }
                    })
                }
            }
            extension.setMessageDelegate(delegate, "browser")
            callback(true, null)
        }, { error ->
            Log.e("NovaShields", "Could not install Shields extension", error)
            callback(false, error?.message ?: "Could not install Shields extension.")
        })
    }

    fun get(host: String, callback: (JSONObject?, String?) -> Unit) {
        val activePort = port
        if (activePort == null) {
            callback(null, "Shields bridge is still starting. Try again in a moment.")
            return
        }
        stateCallback = { state ->
            stateCallback = null
            callback(state, null)
        }
        activePort.postMessage(JSONObject().put("type", "getShield").put("host", host))
    }

    fun setMode(host: String, mode: String, callback: (JSONObject?, String?) -> Unit) {
        val activePort = port
        if (activePort == null) {
            callback(null, "Shields bridge is not connected yet.")
            return
        }
        stateCallback = { state ->
            stateCallback = null
            callback(state, null)
        }
        activePort.postMessage(JSONObject().put("type", "setMode").put("host", host).put("mode", mode))
    }

    fun setSiteMode(host: String, mode: String, callback: (JSONObject?, String?) -> Unit) {
        val activePort = port
        if (activePort == null) {
            callback(null, "Shields bridge is not connected yet.")
            return
        }
        stateCallback = { state ->
            stateCallback = null
            callback(state, null)
        }
        activePort.postMessage(JSONObject().put("type", "setSiteMode").put("host", host).put("mode", mode))
    }

    fun setScripts(host: String, blocked: Boolean, callback: (JSONObject?, String?) -> Unit) {
        val activePort = port
        if (activePort == null) {
            callback(null, "Shields bridge is not connected yet.")
            return
        }
        stateCallback = { state ->
            stateCallback = null
            callback(state, null)
        }
        activePort.postMessage(JSONObject().put("type", "setScripts").put("host", host).put("blocked", blocked))
    }

    fun update(host: String, callback: (JSONObject?, String?) -> Unit) {
        val activePort = port
        if (activePort == null) {
            callback(null, "Shields bridge is not connected yet.")
            return
        }
        stateCallback = { state ->
            stateCallback = null
            callback(state, null)
        }
        activePort.postMessage(JSONObject().put("type", "update").put("host", host))
    }
}
