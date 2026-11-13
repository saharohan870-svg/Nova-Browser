package com.zorvi.browser

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import org.torproject.jni.TorService
import java.util.concurrent.Executors

/**
 * Owns the embedded Guardian Project TorService while Nova Guest Mode is active.
 * Reports ready only after Tor reports a completed bootstrap (PROGRESS=100).
 */
class NovaTorController(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var service: TorService? = null
    private var bound = false
    private var startedAt = 0L
    private var callback: ((Boolean, Int, String?) -> Unit)? = null
    private var pollInFlight = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? TorService.LocalBinder)?.service
            if (service == null) {
                finish(false, 0, "Tor service could not be bound.")
                return
            }
            startedAt = SystemClock.elapsedRealtime()
            pollBootstrap()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            if (callback != null) finish(false, 0, "Tor service disconnected before its circuit was ready.")
        }
    }

    fun start(onReady: (Boolean, Int, String?) -> Unit) {
        callback = onReady
        if (bound && service != null) {
            startedAt = SystemClock.elapsedRealtime()
            pollBootstrap()
            return
        }
        val connected = runCatching {
            appContext.bindService(
                Intent(appContext, TorService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
        }.getOrDefault(false)
        if (!connected) finish(false, 0, "Android could not start the embedded Tor service.")
        else bound = true
    }

    private fun pollBootstrap() {
        if (pollInFlight) return
        val activeService = service ?: return
        pollInFlight = true
        worker.execute {
            val port = runCatching { activeService.getSocksPort() }.getOrDefault(-1)
            val phase = runCatching { activeService.getInfo("status/bootstrap-phase").orEmpty() }.getOrDefault("")
            val ready = port in 1..65535 && phase.contains("PROGRESS=100")
            mainHandler.post {
                pollInFlight = false
                if (callback == null) return@post
                if (ready) {
                    finish(true, port, null)
                } else if (SystemClock.elapsedRealtime() - startedAt >= 120_000L) {
                    finish(false, 0, "Tor did not finish bootstrapping within 120 seconds. Check your connection and try again.")
                } else {
                    mainHandler.postDelayed({ pollBootstrap() }, 1_000L)
                }
            }
        }
    }

    private fun finish(ok: Boolean, port: Int, message: String?) {
        val result = callback ?: return
        callback = null
        result(ok, port, message)
    }

    fun stop() {
        callback = null
        mainHandler.removeCallbacksAndMessages(null)
        service = null
        pollInFlight = false
        if (bound) {
            runCatching { appContext.unbindService(connection) }
            bound = false
        }
    }
}
