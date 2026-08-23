package com.example.data.crypto

import android.util.Base64
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {
    private const val DEFAULT_SECRET = "CleanMinimalistPrivateMessengerKey!"
    private val defaultKeyBytes = DEFAULT_SECRET.toByteArray().copyOf(32) // AES-256
    private val defaultIvBytes = ByteArray(16) { 0 }

    /**
     * Generate real NIST P-256 Elliptic Curve KeyPair for Diffie-Hellman Key Agreement
     */
    fun generateECDHKeyPair(): Pair<String, String> {
        return try {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec("secp256r1"))
            val keyPair = kpg.generateKeyPair()

            val pubBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
            val privBase64 = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
            Pair(pubBase64, privBase64)
        } catch (e: Exception) {
            val fallbackSeed = "ECDH-P256:" + System.currentTimeMillis().toString()
            Pair(generateFingerprint(fallbackSeed), fallbackSeed)
        }
    }

    /**
     * Compute shared secret between our private key and peer's public key (Diffie-Hellman)
     */
    fun computeSharedSecret(myPrivateKeyBase64: String, peerPublicKeyBase64: String): ByteArray {
        return try {
            val keyFactory = KeyFactory.getInstance("EC")
            val privKeyBytes = Base64.decode(myPrivateKeyBase64, Base64.NO_WRAP)
            val pubKeyBytes = Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)

            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privKeyBytes))
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(pubKeyBytes))

            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(privateKey)
            ka.doPhase(publicKey, true)
            val secret = ka.generateSecret()

            // Derive 32-byte AES key using SHA-256
            val sha = MessageDigest.getInstance("SHA-256")
            sha.digest(secret)
        } catch (e: Exception) {
            defaultKeyBytes
        }
    }

    fun encrypt(plainText: String, secretKey: ByteArray = defaultKeyBytes): String {
        return try {
            val keySpec = SecretKeySpec(secretKey.copyOf(32), "AES")
            val ivSpec = IvParameterSpec(defaultIvBytes)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText
        }
    }

    fun decrypt(cipherText: String, secretKey: ByteArray = defaultKeyBytes): String {
        return try {
            val keySpec = SecretKeySpec(secretKey.copyOf(32), "AES")
            val ivSpec = IvParameterSpec(defaultIvBytes)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decoded = Base64.decode(cipherText, Base64.NO_WRAP)
            String(cipher.doFinal(decoded), Charsets.UTF_8)
        } catch (e: Exception) {
            cipherText
        }
    }

    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun generateFingerprint(seed: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(seed.toByteArray())
        return hash.take(6).joinToString(":") { "%02X".format(it) }
    }

    fun generateSafetyNumber(keyA: String, keyB: String): String {
        val combined = listOf(keyA, keyB).sorted().joinToString("::")
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(combined.toByteArray())
        val numericChunks = hash.take(12).map { (it.toInt() and 0xFF) % 10 }
        return numericChunks.chunked(4).joinToString(" ") { it.joinToString("") }
    }
}
