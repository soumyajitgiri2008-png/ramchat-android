package com.ramchat.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 1000
    private const val KEY_LENGTH_BITS = 256

    // Fixed salt so that both peers derive the exact same key from the same room password.
    private val SALT = "RAM_CHAT_SHARED_SALT_VALUE_2026".toByteArray(Charsets.UTF_8)

    /**
     * Derives a 256-bit AES key from the room password.
     */
    fun deriveKey(password: String): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), SALT, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts a string using AES-GCM and returns a Base64-encoded string containing IV + ciphertext.
     */
    fun encryptText(text: String, secretKey: SecretKey): String {
        val cipherTextBytes = encryptBytes(text.toByteArray(Charsets.UTF_8), secretKey)
        return Base64.encodeToString(cipherTextBytes, Base64.NO_WRAP)
    }

    /**
     * Decrypts a Base64-encoded string containing IV + ciphertext and returns the plain text string.
     */
    fun decryptText(encryptedTextBase64: String, secretKey: SecretKey): String {
        val cipherTextBytes = Base64.decode(encryptedTextBase64, Base64.NO_WRAP)
        val plainBytes = decryptBytes(cipherTextBytes, secretKey)
        return String(plainBytes, Charsets.UTF_8)
    }

    /**
     * Encrypts a byte array and prepends a random 12-byte IV.
     */
    fun encryptBytes(plainBytes: ByteArray, secretKey: SecretKey): ByteArray {
        val iv = ByteArray(IV_LENGTH_BYTES)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val cipherText = cipher.doFinal(plainBytes)

        // Concatenate IV + Ciphertext
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

        return combined
    }

    /**
     * Decrypts a combined byte array (IV + ciphertext) using the secret key.
     */
    fun decryptBytes(combinedBytes: ByteArray, secretKey: SecretKey): ByteArray {
        require(combinedBytes.size > IV_LENGTH_BYTES) { "Invalid cipher text size" }

        val iv = ByteArray(IV_LENGTH_BYTES)
        System.arraycopy(combinedBytes, 0, iv, 0, IV_LENGTH_BYTES)

        val cipherTextSize = combinedBytes.size - IV_LENGTH_BYTES
        val cipherText = ByteArray(cipherTextSize)
        System.arraycopy(combinedBytes, IV_LENGTH_BYTES, cipherText, 0, cipherTextSize)

        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        return cipher.doFinal(cipherText)
    }
}
