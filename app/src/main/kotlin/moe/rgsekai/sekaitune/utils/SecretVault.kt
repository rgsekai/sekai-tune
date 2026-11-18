// SecretVault.kt

package moe.rgsekai.sekaitune.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import timber.log.Timber
import java.security.KeyStore
import java.util.Collections
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// seals the app's secrets with a key that lives in the phone's keystore and never leaves it
object SecretVault {
    private const val Alias = "sekaitune_secrets"
    private const val Prefix = "enc1:"
    private const val IvBytes = 12
    private const val TagBits = 128
    private const val Transformation = "AES/GCM/NoPadding"

    @Volatile private var key: SecretKey? = null
    @Volatile private var broken = false

    private val opened = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 64
        },
    )

    fun isSealed(text: String) = text.startsWith(Prefix)

    fun seal(plain: String): String {
        if (isSealed(plain)) return plain
        val key = keyOrNull() ?: return plain
        return runCatching {
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val sealed = Prefix + Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            opened[sealed] = plain
            sealed
        }.getOrElse { plain }
    }

    fun open(stored: String): String? {
        if (!isSealed(stored)) return stored
        opened[stored]?.let { return it }
        val key = keyOrNull() ?: return null
        return runCatching {
            val bytes = Base64.decode(stored.removePrefix(Prefix), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TagBits, bytes, 0, IvBytes))
            String(cipher.doFinal(bytes, IvBytes, bytes.size - IvBytes), Charsets.UTF_8)
        }.getOrNull()?.also { opened[stored] = it }
    }

    private fun keyOrNull(): SecretKey? {
        key?.let { return it }
        if (broken) return null
        synchronized(this) {
            key?.let { return it }
            if (broken) return null
            return runCatching { loadOrMake() }
                .onFailure {
                    broken = true
                    Timber.tag("Vault").w(it, "keystore unusable, secrets stay plain")
                }
                .getOrNull()
                ?.also { key = it }
        }
    }

    private fun loadOrMake(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(Alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(Alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}

