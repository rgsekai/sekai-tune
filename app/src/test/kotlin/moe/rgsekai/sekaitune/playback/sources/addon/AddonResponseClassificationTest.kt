/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources.addon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class AddonResponseClassificationTest {

    @Test
    fun testIsNoMatchHttp_detectsNoMatchCases() {
        // HTTP 404 is always NO_MATCH
        assertTrue(AddonResponseClassifier.isNoMatchHttp(404, "Not Found"))
        assertTrue(AddonResponseClassifier.isNoMatchHttp(404, ""))
        assertTrue(AddonResponseClassifier.isNoMatchHttp(404, null))

        // HTTP 403 with unavailable body (e.g. Tido 403)
        assertTrue(AddonResponseClassifier.isNoMatchHttp(403, """{"error": "Unavailable in this region"}"""))
        assertTrue(AddonResponseClassifier.isNoMatchHttp(403, """{"error": "Unavailable"}"""))
        assertTrue(AddonResponseClassifier.isNoMatchHttp(403, "Track unavailable"))
        assertTrue(AddonResponseClassifier.isNoMatchHttp(403, "not found"))
        assertTrue(AddonResponseClassifier.isNoMatchHttp(403, "no results found"))
        assertTrue(AddonResponseClassifier.isNoMatchHttp(403, ""))

        // HTTP 400 with not found or empty
        assertTrue(AddonResponseClassifier.isNoMatchHttp(400, "item not found"))
        assertTrue(AddonResponseClassifier.isNoMatchHttp(400, "no match"))
        assertTrue(AddonResponseClassifier.isNoMatchHttp(400, null))
    }

    @Test
    fun testIsNoMatchHttp_detectsRealFailures() {
        // 401 Unauthorized
        assertFalse(AddonResponseClassifier.isNoMatchHttp(401, "Invalid token"))

        // 402 Payment required (comes from addon server, not our app)
        assertFalse(AddonResponseClassifier.isNoMatchHttp(402, "Payment required"))

        // 429 Rate limited
        assertFalse(AddonResponseClassifier.isNoMatchHttp(429, "Too many requests"))

        // 5xx Server errors
        assertFalse(AddonResponseClassifier.isNoMatchHttp(500, "Internal Server Error"))
        assertFalse(AddonResponseClassifier.isNoMatchHttp(502, "Bad Gateway"))
        assertFalse(AddonResponseClassifier.isNoMatchHttp(503, "Service Unavailable"))
        assertFalse(AddonResponseClassifier.isNoMatchHttp(504, "Gateway Timeout"))
    }

    @Test
    fun testClassify_matchesExpectedOutcomes() {
        // Success with items
        val successWithItems = AddonResponse.Success(listOf("track1"))
        assertEquals(AddonOutcomeKind.MATCH, AddonResponseClassifier.classify(successWithItems, isEmpty = false))

        // Success with empty list
        val successEmpty = AddonResponse.Success(emptyList<String>())
        assertEquals(AddonOutcomeKind.NO_MATCH, AddonResponseClassifier.classify(successEmpty, isEmpty = true))

        // 404 is NO_MATCH
        val http404 = AddonResponse.HttpError(404, "Not found")
        assertEquals(AddonOutcomeKind.NO_MATCH, AddonResponseClassifier.classify(http404))

        // 403 Unavailable is NO_MATCH
        val tido403 = AddonResponse.HttpError(403, """{"error": "Unavailable in this region"}""")
        assertEquals(AddonOutcomeKind.NO_MATCH, AddonResponseClassifier.classify(tido403))

        // 401 is REAL_FAILURE
        val http401 = AddonResponse.HttpError(401, "Unauthorized")
        assertEquals(AddonOutcomeKind.REAL_FAILURE, AddonResponseClassifier.classify(http401))

        // 402 is REAL_FAILURE
        val http402 = AddonResponse.HttpError(402, "Payment required")
        assertEquals(AddonOutcomeKind.REAL_FAILURE, AddonResponseClassifier.classify(http402))

        // 500 is REAL_FAILURE
        val http500 = AddonResponse.HttpError(500, "Crash")
        assertEquals(AddonOutcomeKind.REAL_FAILURE, AddonResponseClassifier.classify(http500))

        // 429 is REAL_FAILURE
        val rateLimited = AddonResponse.RateLimited(15)
        assertEquals(AddonOutcomeKind.REAL_FAILURE, AddonResponseClassifier.classify(rateLimited))

        // Network error is REAL_FAILURE
        val timeout = AddonResponse.NetworkError(SocketTimeoutException("Read timeout"))
        assertEquals(AddonOutcomeKind.REAL_FAILURE, AddonResponseClassifier.classify(timeout))

        // Serialization error is REAL_FAILURE
        val serialization = AddonResponse.SerializationError("bad json", IOException("Malformed"))
        assertEquals(AddonOutcomeKind.REAL_FAILURE, AddonResponseClassifier.classify(serialization))
    }

    @Test
    fun testFormatFailureReason_redactsTokensAndProvidesShortReason() {
        val errorWithToken = AddonResponse.HttpError(401, "Invalid token: token=abcdef1234567890")
        val reason = AddonResponseClassifier.formatFailureReason(errorWithToken)
        assertFalse(reason.contains("abcdef1234567890"))
        assertTrue(reason.contains("HTTP 401"))

        val timeoutError = AddonResponse.NetworkError(SocketTimeoutException())
        assertEquals("Request timed out", AddonResponseClassifier.formatFailureReason(timeoutError))

        val paymentError = AddonResponse.HttpError(402, "Payment required")
        assertEquals("Payment required (HTTP 402)", AddonResponseClassifier.formatFailureReason(paymentError))
    }
}
