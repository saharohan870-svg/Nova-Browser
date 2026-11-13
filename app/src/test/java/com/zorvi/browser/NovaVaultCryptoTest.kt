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

    @Test(expected = javax.crypto.AEADBadTagException::class)
    fun modifiedCiphertextIsRejected() {
        val envelope = NovaVaultCrypto.encrypt("private vault data", "a sufficiently long passphrase".toCharArray())
        val changed = JSONObject(envelope.toString())
        changed.put("ciphertext", changed.getString("ciphertext").dropLast(2) + "AA")
        NovaVaultCrypto.decrypt(changed, "a sufficiently long passphrase".toCharArray())
    }
}
