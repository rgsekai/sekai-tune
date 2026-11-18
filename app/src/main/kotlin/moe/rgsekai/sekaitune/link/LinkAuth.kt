// LinkAuth.kt

package moe.rgsekai.sekaitune.link

import android.util.Base64
import java.net.URLDecoder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

// a device's own id and the key it shares with the one it's talking to; the key signs each call and never goes over the wi-fi itself
class LinkCredentials(val id: String, val key: String)

// how paired devices prove a call is theirs: an hmac over the call with the time and a nonce, so a copied call is no good to anyone listening on the wi-fi
object LinkAuth {
    const val Header = "X-SekaiTune-Auth"
    private const val Scheme = "v2"
    private const val MacName = "HmacSHA256"

    // a tv's clock and a phone's can drift a little; past this the call is turned away
    private const val WindowMs = 10 * 60_000L

    // a song link the player opens stays good this long, longer than any song or download takes
    const val LinkGoodForMs = 12 * 60 * 60_000L

    // the signature a player's link carries in its query, since a player sends no headers of ours
    private const val SignedId = "d"
    private const val Expires = "e"
    private const val Signature = "s"
    private val LinkFields = setOf(SignedId, Expires, Signature)

    private val random = SecureRandom()
    private val NoBody = ByteArray(0)

    // the header for one call, made fresh each time
    fun header(credentials: LinkCredentials, method: String, path: String, query: Map<String, String>, body: ByteArray?): String {
        val time = System.currentTimeMillis()
        val nonce = hex(ByteArray(12).also(random::nextBytes))
        val mac = sign(credentials.key, canonical(method, path, query, time, nonce, body ?: NoBody))
        return "$Scheme ${credentials.id} $time $nonce $mac"
    }

    // the query for a link the player can open with no header; only the path is signed, so a range or a head request is fine
    fun signedQuery(credentials: LinkCredentials, path: String, expiresAt: Long): String {
        val mac = sign(credentials.key, canonical("GET", path, emptyMap(), expiresAt, "", NoBody))
        return "$SignedId=${java.net.URLEncoder.encode(credentials.id, "UTF-8")}&$Expires=$expiresAt&$Signature=$mac"
    }

    // a query string split the one way both sides agree on, so what's signed is what's checked
    fun parseQuery(encoded: String?): Map<String, String> =
        encoded.orEmpty().split('&').filter { it.isNotEmpty() }.associate { pair ->
            URLDecoder.decode(pair.substringBefore('='), "UTF-8") to URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
        }

    // one per server: remembers the nonces it has seen, so a call someone copied can't be played again
    class Verifier {
        private val seen = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 2048
        }

        // the sender's id when the call checks out against the key [keyOf] gives for that id, null otherwise
        @Synchronized
        fun verify(request: LinkRequest, keyOf: (id: String) -> String?): String? {
            if (request.query.containsKey(Signature)) return verifyLink(request, keyOf)
            val parts = request.header(Header)?.split(' ') ?: return null
            if (parts.size != 5 || parts[0] != Scheme) return null
            val id = parts[1]
            val time = parts[2].toLongOrNull() ?: return null
            val nonce = parts[3]
            if (abs(System.currentTimeMillis() - time) > WindowMs) return null
            val key = keyOf(id) ?: return null
            val expected = sign(key, canonical(request.method, request.path, request.query, time, nonce, request.body))
            if (!same(expected, parts[4])) return null
            // the same call twice is a copy, the first one already ran
            if (seen.put("$id:$nonce", time) != null) return null
            return id
        }

        private fun verifyLink(request: LinkRequest, keyOf: (id: String) -> String?): String? {
            val id = request.query[SignedId] ?: return null
            val expiresAt = request.query[Expires]?.toLongOrNull() ?: return null
            if (expiresAt < System.currentTimeMillis()) return null
            val key = keyOf(id) ?: return null
            val expected = sign(key, canonical("GET", request.path, emptyMap(), expiresAt, "", NoBody))
            return if (same(expected, request.query[Signature].orEmpty())) id else null
        }
    }

    private fun canonical(method: String, path: String, query: Map<String, String>, time: Long, nonce: String, body: ByteArray): String {
        val fields = query.filterKeys { it !in LinkFields }.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
        return listOf(method.uppercase(), path, fields, time.toString(), nonce, hex(MessageDigest.getInstance("SHA-256").digest(body))).joinToString("\n")
    }

    private fun sign(key: String, text: String): String {
        val mac = Mac.getInstance(MacName)
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), MacName))
        return hex(mac.doFinal(text.toByteArray(Charsets.UTF_8)))
    }

    // compared in constant time, so the length of a near miss gives nothing away
    private fun same(expected: String, given: String) = MessageDigest.isEqual(expected.toByteArray(), given.toByteArray())

    // pairing: each side makes a key pair and sends only its public half; both then work out the same key from the two halves and the code on the screen
    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    fun publicText(pair: KeyPair): String = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)

    // null when the other side's public half doesn't parse; someone who only listened in has neither private half, and someone without the code has the wrong key
    fun sharedKey(pair: KeyPair, otherPublic: String, code: String, ids: List<String>): String? = runCatching {
        val other = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(otherPublic, Base64.NO_WRAP)))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(pair.private)
        agreement.doPhase(other, true)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("sekaitune-link-v2".toByteArray())
        digest.update(agreement.generateSecret())
        digest.update(code.trim().toByteArray())
        ids.sorted().forEach { digest.update(it.toByteArray()) }
        hex(digest.digest())
    }.getOrNull()

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}

