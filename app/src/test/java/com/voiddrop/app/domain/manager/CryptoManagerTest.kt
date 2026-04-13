package com.voiddrop.app.domain.manager

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CryptoManagerTest {

    private val cryptoManager = CryptoManager()

    @Test
    fun `encrypt and decrypt returns original data`() {
        val key = cryptoManager.generateKey()
        val originalData = "Hello VoidDrop Encrypted World".toByteArray()

        val encryptedData = cryptoManager.encrypt(originalData, key)
        val decryptedData = cryptoManager.decrypt(encryptedData, key)

        assertArrayEquals(originalData, decryptedData)
    }

    @Test
    fun `encrypting same data twice produces different outputs due to random IV`() {
        val key = cryptoManager.generateKey()
        val data = "Sensitive Data".toByteArray()

        val enc1 = cryptoManager.encrypt(data, key)
        val enc2 = cryptoManager.encrypt(data, key)

        assertFalse("Ciphertext should differ (IV uniqueness)", enc1.contentEquals(enc2))
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun `decrypt throws exception on invalid data`() {
        val key = cryptoManager.generateKey()
        val invalidData = ByteArray(10) // Too short for IV
        cryptoManager.decrypt(invalidData, key)
    }
}
