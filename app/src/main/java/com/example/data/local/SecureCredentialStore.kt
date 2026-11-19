package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.WorkerThread
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small app-private credential vault backed by an AES-256-GCM key in Android Keystore.
 *
 * This is storage infrastructure only. It does not authenticate an administrator,
 * issue tokens, or connect to a backend. Call all methods from a worker thread.
 *
 * Encrypted preference values are intentionally unreadable after restore to a
 * different device/Keystore; callers must treat decryption failure as unavailable
 * credentials and require the user to pair/authenticate again.
 */
class SecureCredentialStore(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @WorkerThread
    @Synchronized
    fun put(credentialId: String, value: String) {
        requireValidId(credentialId)
        require(value.isNotEmpty()) { "Credential value must not be empty." }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val prefix = storagePrefix(credentialId)
        val saved = preferences.edit()
            .putString(prefix + IV_SUFFIX, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(prefix + VALUE_SUFFIX, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()
        check(saved) { "Unable to persist encrypted credential." }
    }

    @WorkerThread
    @Synchronized
    fun get(credentialId: String): String? {
        requireValidId(credentialId)
        val prefix = storagePrefix(credentialId)
        val ivText = preferences.getString(prefix + IV_SUFFIX, null) ?: return null
        val encryptedText = preferences.getString(prefix + VALUE_SUFFIX, null)
            ?: throw IllegalStateException("Encrypted credential data is incomplete.")

        val iv = Base64.decode(ivText, Base64.NO_WRAP)
        val encrypted = Base64.decode(encryptedText, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }

    @WorkerThread
    @Synchronized
    fun remove(credentialId: String) {
        requireValidId(credentialId)
        val prefix = storagePrefix(credentialId)
        val removed = preferences.edit()
            .remove(prefix + IV_SUFFIX)
            .remove(prefix + VALUE_SUFFIX)
            .commit()
        check(removed) { "Unable to remove encrypted credential." }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(KEY_SIZE_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun requireValidId(credentialId: String) {
        require(credentialId.isNotBlank() && credentialId.length <= MAX_ID_LENGTH) {
            "Credential ID must contain 1-$MAX_ID_LENGTH characters."
        }
    }

    private fun storagePrefix(credentialId: String): String =
        CREDENTIAL_PREFIX + Base64.encodeToString(
            credentialId.toByteArray(StandardCharsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        ) + "_"

    private companion object {
        const val PREFERENCES_NAME = "maya_secure_credentials"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "maya_secure_credentials_aes_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_BITS = 128
        const val MAX_ID_LENGTH = 100
        const val CREDENTIAL_PREFIX = "credential_"
        const val IV_SUFFIX = "iv"
        const val VALUE_SUFFIX = "value"
    }
}
