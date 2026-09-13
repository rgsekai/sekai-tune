/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsEncryptionServiceTest {

    @Test
    fun testEncryptionDecryptionRoundTrip() {
        val service = SettingsEncryptionService()
        val originalKey = "sk-proj-1234567890abcdefghijklmnopqrstuvwxyz_TEST_API_KEY"

        val encrypted = service.encrypt(originalKey)
        assertTrue(encrypted.ciphertext.isNotEmpty())
        assertTrue(encrypted.iv.isNotEmpty())
        assertNotEquals(originalKey, encrypted.ciphertext)

        val decrypted = service.decrypt(encrypted)
        assertEquals(originalKey, decrypted)
    }

    @Test
    fun testEmptyStringHandling() {
        val service = SettingsEncryptionService()
        val encrypted = service.encrypt("")
        assertEquals("", encrypted.ciphertext)
        assertEquals("", encrypted.iv)

        val decrypted = service.decrypt(encrypted)
        assertEquals("", decrypted)
    }

    @Test
    fun testDifferentIvsPerEncryption() {
        val service = SettingsEncryptionService()
        val plaintext = "sensitive_password_value_123"

        val enc1 = service.encrypt(plaintext)
        val enc2 = service.encrypt(plaintext)

        // AES-GCM must use random unique IVs for each encryption operation
        assertNotEquals(enc1.iv, enc2.iv)
        assertNotEquals(enc1.ciphertext, enc2.ciphertext)

        // Both must decrypt back to original plaintext
        assertEquals(plaintext, service.decrypt(enc1))
        assertEquals(plaintext, service.decrypt(enc2))
    }
}
