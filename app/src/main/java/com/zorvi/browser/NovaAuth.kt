package com.zorvi.browser

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth

/**
 * Firebase-backed identity for Nova. Credentials are handled by Firebase Auth;
 * passwords are never stored in Nova preferences or browser history.
 */
internal class NovaAuth(context: Context) {
    val auth: FirebaseAuth?

    init {
        val appContext = context.applicationContext
        val configured = NovaFirebaseConfig.API_KEY.isNotBlank() &&
            NovaFirebaseConfig.APPLICATION_ID.isNotBlank() &&
            NovaFirebaseConfig.PROJECT_ID.isNotBlank()
        auth = if (!configured) {
            null
        } else {
            val options = FirebaseOptions.Builder()
                .setApiKey(NovaFirebaseConfig.API_KEY)
                .setApplicationId(NovaFirebaseConfig.APPLICATION_ID)
                .setProjectId(NovaFirebaseConfig.PROJECT_ID)
                .build()
            val app = FirebaseApp.getApps(appContext).firstOrNull()
                ?: FirebaseApp.initializeApp(appContext, options)
            app?.let { FirebaseAuth.getInstance(it) }
        }
    }

    val isConfigured: Boolean get() = auth != null
}
