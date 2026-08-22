package com.example.data.crypto

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {
    private const val DEFAULT_SECRET = "CleanMinimalistPrivateMessengerKey!"
    private val keyBytes = DEFAULT_SECRET.toByteArray().copyOf(16)
    private val ivBytes = ByteArray(16) { 0 }

    fun encrypt(plainText: String): String {
        return try {
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(ivBytes)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText
        }
    }

    fun decrypt(cipherText: String): String {
        return try {
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(ivBytes)
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
}
