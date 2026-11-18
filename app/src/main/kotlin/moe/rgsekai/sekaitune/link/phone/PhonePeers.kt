// PhonePeers.kt

package moe.rgsekai.sekaitune.link.phone

import android.content.Context
import moe.rgsekai.sekaitune.link.DeviceKind
import moe.rgsekai.sekaitune.link.LinkCredentials
import moe.rgsekai.sekaitune.link.LinkIdentity
import moe.rgsekai.sekaitune.link.LinkWire
import moe.rgsekai.sekaitune.link.discoverPeers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

// while Sekai Tune is on screen, a phone says hello to the other phones and tablets it's paired with, so each knows the other is there to steer
internal class PhonePeers(
    private val context: Context,
    private val host: PhoneLinkHost,
    private val scope: CoroutineScope,
) {
    private var loop: Job? = null

    // looking a peer up on the wi-fi keeps the radio busy for seconds, so a missing one is looked for now and then
    private val lookedAt = ConcurrentHashMap<String, Long>()

    @Synchronized
    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch(Dispatchers.IO) {
            while (isActive) {
                checkAll()
                delay(HeartbeatMs)
            }
        }
    }

    @Synchronized
    fun stop() {
        loop?.cancel()
        loop = null
    }

    fun poke() {
        scope.launch(Dispatchers.IO) { checkAll() }
    }

    private suspend fun checkAll() {
        val myPort = host.port.takeIf { it > 0 } ?: return
        host.devices.value.filter { !it.kind.isTv }.forEach { peer -> reach(peer, myPort) }
    }

    private suspend fun reach(peer: PairedDevice, myPort: Int) {
        val address = peer.host
        if (address != null && hello(peer, address, peer.port, myPort)) return
        val now = System.currentTimeMillis()
        if (now - (lookedAt[peer.id] ?: 0L) < LookEveryMs) return
        lookedAt[peer.id] = now
        // it moved to another address, or never said where it was: find it by its id
        val found = withTimeoutOrNull(LookForMs) {
            discoverPeers(context, LinkWire.PhoneServiceType)
                .first { peers -> peers.any { it.id == peer.id } }
                .first { it.id == peer.id }
        } ?: return
        hello(peer, found.host, found.port, myPort)
    }

    // true when the peer answered, whatever it said
    private fun hello(peer: PairedDevice, address: String, port: Int, myPort: Int): Boolean = try {
        val url = "${LinkWire.baseUrl(address, port)}/hello?port=$myPort&kind=${DeviceKind.of(context).wire}"
        LinkWire.get(url, LinkCredentials(LinkIdentity.id(context), peer.key)).use { response ->
            when (response.code) {
                200 -> {
                    host.markLive(peer, address, port)
                    true
                }
                // the other phone unlinked this one while they were apart
                401 -> {
                    host.forget(peer.id)
                    true
                }
                else -> false
            }
        }
    } catch (_: IOException) {
        false
    }

    private companion object {
        const val HeartbeatMs = 20_000L
        const val LookForMs = 6_000L
        const val LookEveryMs = 2 * 60_000L
    }
}

