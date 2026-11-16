package com.example.picup.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Where access tokens live.
 *
 * The token is the one credential this app holds, so it is encrypted with a
 * AES-256-GCM key that is generated inside the Android Keystore and never
 * leaves it. The ciphertext lives in a private SharedPreferences file; the key
 * material is not exportable, so a copied prefs file is useless off-device.
 *
 * Nothing is uploaded anywhere: PicUp has no backend, the token is sent only to
 * the git host the user chose.
 */
interface TokenStore {
    fun read(accountId: String): String?
    fun write(accountId: String, token: String)
    fun delete(accountId: String)

    /** True when [accountId] has a token that can still be decrypted. */
    fun has(accountId: String): Boolean = !read(accountId).isNullOrBlank()
}

class KeystoreTokenStore(context: Context) : TokenStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(accountId: String): String? {
        val stored = prefs.getString(accountId, null) ?: return null
        return runCatching {
            val (ivPart, cipherPart) = stored.split(':', limit = 2).also {
                check(it.size == 2) { "Malformed token record" }
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(ivPart)),
            )
            String(cipher.doFinal(Base64.decode(cipherPart)), Charsets.UTF_8)
        }.getOrElse { error ->
            // A restored backup has ciphertext but not the device-bound key.
            Log.w(TAG, "Cannot decrypt token for $accountId", error)
            null
        }
    }

    override fun write(accountId: String, token: String) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val cipherText = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            val record = Base64.encode(cipher.iv) + ":" + Base64.encode(cipherText)
            prefs.edit().putString(accountId, record).apply()
        }.onFailure { error ->
            Log.w(TAG, "Cannot store token for $accountId", error)
        }
    }

    override fun delete(accountId: String) {
        prefs.edit().remove(accountId).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // No user authentication requirement: an unattended upload from
                // the share flow must not pop a biometric prompt.
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private object Base64 {
        fun encode(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)
        fun decode(text: String): ByteArray = java.util.Base64.getDecoder().decode(text)
    }

    private companion object {
        const val TAG = "PicUpTokens"
        const val PREFS_NAME = "picup.tokens"
        const val KEY_ALIAS = "picup-token-key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}

/** Used by unit tests and by previews. */
class InMemoryTokenStore : TokenStore {
    private val tokens = mutableMapOf<String, String>()

    override fun read(accountId: String): String? = tokens[accountId]

    override fun write(accountId: String, token: String) {
        tokens[accountId] = token
    }

    override fun delete(accountId: String) {
        tokens.remove(accountId)
    }
}