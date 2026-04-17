package com.voiddrop.app.domain.manager

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdentityManager @Inject constructor() {

    private val secureRandom = SecureRandom()

    fun getPublicKeyBase64(): String {
        val publicKey = getOrCreateKeyPair().public
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    fun sign(payload: String): String {
        val keyPair = getOrCreateKeyPair()
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(keyPair.private)
        signature.update(payload.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }

    fun verify(publicKeyBase64: String, payload: String, signatureBase64: String): Boolean {
        return try {
            val publicKey = parsePublicKey(publicKeyBase64)
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initVerify(publicKey)
            signature.update(payload.toByteArray(StandardCharsets.UTF_8))
            signature.verify(Base64.decode(signatureBase64, Base64.NO_WRAP))
        } catch (_: Exception) {
            false
        }
    }

    fun generateNonce(byteLength: Int = 16): String {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun computeVerificationCode(
        localPublicKeyBase64: String,
        remotePublicKeyBase64: String,
        sessionCode: String,
        localNonce: String,
        remoteNonce: String
    ): String {
        val keyMaterial = listOf(localPublicKeyBase64, remotePublicKeyBase64).sorted().joinToString("|")
        val nonceMaterial = listOf(localNonce, remoteNonce).sorted().joinToString("|")
        val data = "$sessionCode|$keyMaterial|$nonceMaterial"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray(StandardCharsets.UTF_8))

        val value = (
            ((digest[0].toLong() and 0xFF) shl 24) or
                ((digest[1].toLong() and 0xFF) shl 16) or
                ((digest[2].toLong() and 0xFF) shl 8) or
                (digest[3].toLong() and 0xFF)
            ) % 1_000_000L

        return String.format(Locale.US, "%06d", value)
    }

    @Synchronized
    private fun getOrCreateKeyPair(): KeyPair {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        val existingPrivateKey = keyStore.getKey(KEY_ALIAS, null)
        val existingCertificate = keyStore.getCertificate(KEY_ALIAS)
        if (existingPrivateKey != null && existingCertificate != null) {
            return KeyPair(existingCertificate.publicKey, existingPrivateKey as java.security.PrivateKey)
        }

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )

        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setUserAuthenticationRequired(false)
            .build()

        keyPairGenerator.initialize(keySpec)
        return keyPairGenerator.generateKeyPair()
    }

    private fun parsePublicKey(publicKeyBase64: String): PublicKey {
        val publicBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(publicBytes)
        val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC)
        return keyFactory.generatePublic(keySpec)
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "voiddrop_device_identity"
        private const val EC_CURVE = "secp256r1"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }
}
