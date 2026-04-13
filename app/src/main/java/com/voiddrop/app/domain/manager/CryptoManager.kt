package com.voiddrop.app.domain.manager

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoManager @Inject constructor() {

    private val secureRandom = SecureRandom()
    
    private val cipherThreadLocal = object : ThreadLocal<Cipher>() {
        override fun initialValue(): Cipher {
            return Cipher.getInstance(TRANSFORMATION)
        }
    }

    // AES-256-GCM
    // Key Size: 256 bits
    // IV Size: 96 bits (12 bytes) - NIST Recommendation
    // Tag Size: 128 bits (16 bytes) - GCM Default

    fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
        keyGenerator.init(KEY_SIZE, secureRandom)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts data using AES-GCM.
     * Prepends the random IV to the output ciphertext.
     * Output format: [IV (12 bytes)] [Ciphertext + Auth Tag]
     */
    fun encrypt(data: ByteArray, key: SecretKey): ByteArray {
        val cipher = cipherThreadLocal.get()!!
        val iv = ByteArray(IV_SIZE)
        secureRandom.nextBytes(iv)
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val ciphertext = cipher.doFinal(data)
        
        // Combine IV + Ciphertext
        val combined = ByteArray(IV_SIZE + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, IV_SIZE)
        System.arraycopy(ciphertext, 0, combined, IV_SIZE, ciphertext.size)
        
        return combined
    }

    /**
     * Decrypts AES-GCM data.
     * Expects input to contain the prepended 12-byte IV.
     */
    fun decrypt(encryptedDataWithIv: ByteArray, key: SecretKey): ByteArray {
        if (encryptedDataWithIv.size < IV_SIZE) {
            throw IllegalArgumentException("Invalid encrypted data size (too short)")
        }
        
        val iv = ByteArray(IV_SIZE)
        System.arraycopy(encryptedDataWithIv, 0, iv, 0, IV_SIZE)
        
        val ciphertextLength = encryptedDataWithIv.size - IV_SIZE
        val ciphertext = ByteArray(ciphertextLength)
        System.arraycopy(encryptedDataWithIv, IV_SIZE, ciphertext, 0, ciphertextLength)
        
        val cipher = cipherThreadLocal.get()!!
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(ciphertext)
    }

    fun keyToBytes(key: SecretKey): ByteArray {
        return key.encoded
    }

    fun bytesToKey(bytes: ByteArray): SecretKey {
        return SecretKeySpec(bytes, ALGORITHM)
    }

    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 256
        private const val IV_SIZE = 12 
        private const val TAG_LENGTH = 128
    }
}
