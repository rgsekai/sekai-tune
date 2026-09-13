/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.sync

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class SettingsEncryptionService @Inject constructor() {

    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "sekaitune_settings_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val AES_KEY_SIZE = 256
    }

    data class EncryptedValue(
        val ciphertext: String,
        val iv: String,
    )

    private val keyStore: KeyStore? by lazy {
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply {
                load(null)
            }
        } catch (e: Exception) {
            Timber.tag("SettingsEncryption").w(e, "AndroidKeyStore unavailable")
            null
        }
    }

    private var fallbackTestKey: SecretKey? = null

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val ks = keyStore
        if (ks != null) {
            try {
                if (ks.containsAlias(KEY_ALIAS)) {
                    val entry = ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                    if (entry != null) {
                        return entry.secretKey
                    }
                }
            } catch (e: Exception) {
                Timber.tag("SettingsEncryption").w(e, "Error loading key from KeyStore, will recreate")
            }

            try {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE_PROVIDER
                )
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(AES_KEY_SIZE)
                    .build()

                keyGenerator.init(keyGenParameterSpec)
                return keyGenerator.generateKey()
            } catch (e: Exception) {
                Timber.tag("SettingsEncryption").w(e, "Failed to generate KeyStore key, using fallback")
            }
        }

        // Fallback for JVM test environments where AndroidKeyStore provider is unavailable
        fallbackTestKey?.let { return it }
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(AES_KEY_SIZE)
        val key = keyGenerator.generateKey()
        fallbackTestKey = key
        return key
    }

    /**
     * Encrypts a plaintext string using genuine AES-256-GCM.
     * Returns an EncryptedValue containing Base64-encoded ciphertext and IV.
     */
    fun encrypt(plaintext: String): EncryptedValue {
        if (plaintext.isEmpty()) {
            return EncryptedValue(ciphertext = "", iv = "")
        }
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val encoder = Base64.getEncoder()
        return EncryptedValue(
            ciphertext = encoder.encodeToString(encryptedBytes),
            iv = encoder.encodeToString(iv),
        )
    }

    /**
     * Decrypts an EncryptedValue back into the original plaintext string using AES-256-GCM.
     */
    fun decrypt(encryptedValue: EncryptedValue): String {
        if (encryptedValue.ciphertext.isEmpty() || encryptedValue.iv.isEmpty()) {
            return ""
        }
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val decoder = Base64.getDecoder()
        val ivBytes = decoder.decode(encryptedValue.iv)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, ivBytes)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val decryptedBytes = cipher.doFinal(decoder.decode(encryptedValue.ciphertext))
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
