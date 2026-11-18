// PhoneFiles.kt

package moe.rgsekai.sekaitune.link

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder

// a song the linked phone keeps a file of: one of its downloads, or a song file from its own storage
data class PhoneFile(val id: String, val bytes: Long, val mime: String, val isLocalFile: Boolean)

// on a tv, the songs the linked phone can hand over, read by the player and the downloads without either knowing about profiles
object PhoneFiles {
    // the player caches a phone's stream under this, so it never mixes with youtube's copy of the same song
    const val CacheKeyPrefix = "phone|"

    private class Remote(val baseUrl: String, val auth: LinkCredentials)

    @Volatile private var remote: Remote? = null

    private val _files = MutableStateFlow<Map<String, PhoneFile>>(emptyMap())
    val files: StateFlow<Map<String, PhoneFile>> = _files.asStateFlow()

    private val _reachable = MutableStateFlow(false)

    // true while the last call to the phone got through
    val reachable: StateFlow<Boolean> = _reachable.asStateFlow()

    // the phone's list arrives with every sync; the address can change while the list stays the same
    fun update(files: Map<String, PhoneFile>) {
        _files.value = files
    }

    fun reachedAt(baseUrl: String, auth: LinkCredentials) {
        remote = Remote(baseUrl, auth)
        _reachable.value = true
    }

    fun lost() {
        _reachable.value = false
    }

    // a profile without a phone, or a switch to another one, forgets everything the last phone said
    fun clear() {
        remote = null
        _files.value = emptyMap()
        _reachable.value = false
    }

    fun has(songId: String): Boolean = _files.value.containsKey(songId)

    // where to stream a song from right now, null when the phone doesn't have it or can't be reached; signed in the query, since the player sends no headers of ours
    fun streamUrl(songId: String): String? {
        if (!_reachable.value || !has(songId)) return null
        val at = remote ?: return null
        val signed = LinkAuth.signedQuery(at.auth, "/file/$songId", System.currentTimeMillis() + LinkAuth.LinkGoodForMs)
        return "${at.baseUrl}/file/${URLEncoder.encode(songId, "UTF-8")}?$signed"
    }
}

