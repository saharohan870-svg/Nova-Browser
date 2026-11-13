package com.zorvi.browser

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Client-side encryption format for Nova's cross-device secret vault.
 *
 * The passphrase is never sent to Supabase or persisted by this class. Only the
 * authenticated ciphertext envelope is suitable for cloud storage.
 */
internal object NovaVaultCrypto {
    private const val FORMAT_VERSION = 1
    private const val KDF_ITERATIONS = 310_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val AAD = "NovaEncryptedVault:v1"
    private val random = SecureRandom()

    fun encrypt(plainText: String, passphrase: CharArray): JSONObject {
        require(passphrase.size >= 12) { "Choose a sync passphrase with at least 12 characters." }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val key = deriveKey(passphrase, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(AAD.toByteArray(StandardCharsets.UTF_8))
            val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            JSONObject()
                .put("version", FORMAT_VERSION)
                .put("kdf", "PBKDF2WithHmacSHA256")
                .put("iterations", KDF_ITERATIONS)
                .put("cipher", "AES-256-GCM")
                .put("salt", encode(salt))
                .put("nonce", encode(nonce))
                .put("ciphertext", encode(encrypted))
        } finally {
            key.encoded?.fill(0)
        }
    }

    fun decrypt(envelope: JSONObject, passphrase: CharArray): String {
        require(envelope.optInt("version", -1) == FORMAT_VERSION) {
            "This Nova vault format is not supported by this version."
        }
        require(envelope.optString("kdf") == "PBKDF2WithHmacSHA256" &&
            envelope.optString("cipher") == "AES-256-GCM") {
            "The Nova vault encryption settings are invalid."
        }
        val iterations = envelope.optInt("iterations", -1)
        require(iterations in 100_000..1_000_000) { "The Nova vault key-derivation settings are invalid." }
        val salt = decode(envelope.getString("salt"))
        val nonce = decode(envelope.getString("nonce"))
        val ciphertext = decode(envelope.getString("ciphertext"))
        require(salt.size == SALT_BYTES && nonce.size == NONCE_BYTES) {
            "The Nova vault encryption envelope is damaged."
        }
        val key = deriveKey(passphrase, salt, iterations)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(AAD.toByteArray(StandardCharsets.UTF_8))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } finally {
            key.encoded?.fill(0)
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int = KDF_ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP)
}
