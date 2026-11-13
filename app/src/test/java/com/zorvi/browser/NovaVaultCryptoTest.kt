package com.zorvi.browser

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NovaVaultCryptoTest {
    @Test
    fun encryptDecryptRoundTrip() {
        val passphrase = "correct horse battery staple".toCharArray()
        val plaintext = """{"entries":[{"site":"example.com","username":"alice","password":"secret"}]}"""
        val envelope = NovaVaultCrypto.encrypt(plaintext, passphrase)
        assertEquals(1, envelope.getInt("version"))
        assertNotEquals(plaintext, envelope.toString())
        assertEquals(plaintext, NovaVaultCrypto.decrypt(envelope, passphrase))
    }

    @Test(expected = javax.crypto.AEADBadTagException::class)
    fun wrongPassphraseCannotDecrypt() {
        val envelope = NovaVaultCrypto.encrypt("private vault data", "a sufficiently long passphrase".toCharArray())
        NovaVaultCrypto.decrypt(envelope, "a different long passphrase".toCharArray())
    }

    @Test
    fun modifiedCiphertextIsRejected() {
        val envelope = NovaVaultCrypto.encrypt("private vault data", "a sufficiently long passphrase".toCharArray())
        val changed = JSONObject(envelope.toString())
        val bytes = java.util.Base64.getDecoder().decode(changed.getString("ciphertext"))
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        changed.put("ciphertext", java.util.Base64.getEncoder().encodeToString(bytes))
        try {
            NovaVaultCrypto.decrypt(changed, "a sufficiently long passphrase".toCharArray())
            throw AssertionError("Modified ciphertext must not decrypt.")
        } catch (_: javax.crypto.AEADBadTagException) {
            // Expected: GCM authentication rejects modified ciphertext.
        }
    }
}}
