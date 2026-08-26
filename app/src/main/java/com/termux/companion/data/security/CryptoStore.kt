package com.termux.companion.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small Android-Keystore-backed AES/GCM envelope for secrets that must not sit in
 * plaintext DataStore (see FIX-003). Ciphertext is stored as `enc:v1:<base64(iv|ct)>`;
 * values without the prefix pass through untouched so legacy plaintext migrates lazily.
 */
@Singleton
class CryptoStore @Inject constructor() {

    companion object {
        private const val TAG = "CryptoStore"
        private const val KEY_ALIAS = "tc_secret_aes"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE_BYTES = 12
        private const val PREFIX = "enc:v1:"
    }

    fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    /** Returns the encrypted envelope, or null if Keystore encryption failed. */
    fun encrypt(plaintext: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val blob = iv + ciphertext
        PREFIX + Base64.encodeToString(blob, Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.e(TAG, "Keystore encryption failed", e)
        null
    }

    /**
     * Decrypts an envelope produced by [encrypt]. Values without the prefix are
     * returned unchanged (legacy plaintext passthrough). Returns null on failure.
     */
    fun decrypt(stored: String): String? {
        if (stored.isEmpty()) return ""
        if (!isEncrypted(stored)) return stored
        return try {
            val blob = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            if (blob.size <= IV_SIZE_BYTES) return null
            val spec = GCMParameterSpec(128, blob.copyOfRange(0, IV_SIZE_BYTES))
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
            String(cipher.doFinal(blob.copyOfRange(IV_SIZE_BYTES, blob.size)), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Keystore decryption failed", e)
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
