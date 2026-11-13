package com.zorvi.browser

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions

/** Calls the trusted Firebase OTP backend; no OTP logic or secrets live in the APK. */
internal object NovaEmailOtp {
    private val functions by lazy { FirebaseFunctions.getInstance("asia-south1") }

    fun requestCode(email: String, callback: (Boolean, String?) -> Unit) {
        functions.getHttpsCallable("requestEmailOtp")
            .call(mapOf("email" to email))
            .addOnSuccessListener { result ->
                val message = (result.data as? Map<*, *>)?.get("message") as? String
                callback(true, message ?: "If this address is eligible, a code will arrive shortly.")
            }
            .addOnFailureListener { error ->
                callback(false, error.localizedMessage ?: "Could not send a code.")
            }
    }

    fun verifyCode(email: String, code: String, callback: (Boolean, String?) -> Unit) {
        functions.getHttpsCallable("verifyEmailOtp")
            .call(mapOf("email" to email, "code" to code))
            .addOnSuccessListener { result ->
                val token = (result.data as? Map<*, *>)?.get("token") as? String
                if (token.isNullOrBlank()) {
                    callback(false, "The server returned an invalid sign-in response.")
                } else {
                    FirebaseAuth.getInstance().signInWithCustomToken(token)
                        .addOnSuccessListener { callback(true, null) }
                        .addOnFailureListener { error -> callback(false, error.localizedMessage ?: "Could not complete sign-in.") }
                }
            }
            .addOnFailureListener { error ->
                callback(false, error.localizedMessage ?: "The code could not be verified.")
            }
    }
}
