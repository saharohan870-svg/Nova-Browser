package com.zorvi.engine

import android.util.Log
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dedicated QuickJS runtime for the independent Zorvi engine prototype.
 *
 * Scripts run without Android WebView, V8, network APIs, timers, or a full browser
 * DOM. A small document bridge is installed for the DOM operations implemented here.
 */
internal class ZorviScriptRuntime : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Zorvi-JavaScript").apply { isDaemon = true }
    }
    private val closed = AtomicBoolean(false)
    private var runtime: QuickJs? = null
    private var attachedDocument: ZorviDomDocument? = null

    fun loadDocument(document: ZorviDomDocument, inlineScripts: List<String>) {
        if (closed.get()) return
        executor.execute {
            if (closed.get()) return@execute
            try {
                resetRuntime(document)
                inlineScripts.forEachIndexed { index, source ->
                    if (closed.get()) return@forEachIndexed
                    try {
                        runBlocking(Dispatchers.IO) {
                            runtime?.evaluate<Any?>(
                                source,
                                filename = "inline-script-${index + 1}.js"
                            )
                        }
                    } catch (error: Throwable) {
                        Log.w(TAG, "Inline script ${index + 1} failed: ${error.message}")
                    }
                }
                runtime?.gc()
            } catch (error: Throwable) {
                Log.e(TAG, "Could not initialize JavaScript runtime", error)
            }
        }
    }

    private fun resetRuntime(document: ZorviDomDocument) {
        runtime?.close()
        attachedDocument = document
        val js = QuickJs.create(Dispatchers.IO)
        js.memoryLimit = 12L * 1024L * 1024L
        js.maxStackSize = 256L * 1024L
        runtime = js

        js.define("zorviNative") {
            function("hasElement") { args ->
                document.hasElement(args.getOrNull(0)?.toString().orEmpty())
            }
            function("getText") { args ->
                document.getTextById(args.getOrNull(0)?.toString().orEmpty())
            }
            function("setText") { args ->
                document.setTextById(
                    args.getOrNull(0)?.toString().orEmpty(),
                    args.getOrNull(1)?.toString().orEmpty()
                )
                null
            }
            function("getAttribute") { args ->
                document.getAttributeById(
                    args.getOrNull(0)?.toString().orEmpty(),
                    args.getOrNull(1)?.toString().orEmpty()
                )
            }
            function("setAttribute") { args ->
                document.setAttributeById(
                    args.getOrNull(0)?.toString().orEmpty(),
                    args.getOrNull(1)?.toString().orEmpty(),
                    args.getOrNull(2)?.toString().orEmpty()
                )
                null
            }
        }

        runBlocking(Dispatchers.IO) {
            js.evaluate<Any?>(
                """
                globalThis.document = {
                  getElementById(id) {
                    id = String(id);
                    if (!zorviNative.hasElement(id)) return null;
                    return {
                      get textContent() { return zorviNative.getText(id) ?? ""; },
                      set textContent(value) { zorviNative.setText(id, String(value)); },
                      getAttribute(name) { return zorviNative.getAttribute(id, String(name)); },
                      setAttribute(name, value) {
                        zorviNative.setAttribute(id, String(name), String(value));
                      }
                    };
                  }
                };
                """.trimIndent(),
                filename = "zorvi-dom-bootstrap.js"
            )
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.execute {
            runCatching { runtime?.close() }
            runtime = null
            attachedDocument = null
        }
        executor.shutdown()
    }

    private companion object {
        const val TAG = "ZorviScript"
    }
}
