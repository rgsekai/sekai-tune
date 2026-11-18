// LinkPairingDoor.kt

package moe.rgsekai.sekaitune.link

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

// what a pairing screen shows: the code to type on the other device, the name it will see, and the address for a wi-fi that hides devices
@Immutable
data class PairingCode(val code: String, val deviceName: String, val address: String? = null)

// while a pairing screen is open, the device waits on the wi-fi for someone who types the code it shows, and the caller answers the one who got it right
internal class LinkPairingDoor(
    private val context: Context,
    private val serverName: String,
    // given the code that matched too, since the key both sides work out is mixed from it
    private val onPaired: (body: JSONObject, remoteHost: String, code: String) -> LinkResponse,
) {
    private val server = LinkHttpServer(serverName, ::route)
    private var advert: LinkAdvert? = null
    private val random = SecureRandom()

    @Volatile private var wrongTries = 0

    private val _code = MutableStateFlow<PairingCode?>(null)
    val code: StateFlow<PairingCode?> = _code.asStateFlow()

    @Synchronized
    fun open() {
        if (server.isRunning) return
        val name = LinkIdentity.name(context)
        val port = server.start(LinkWire.PairPort)
        newCode()
        advertise(name, port)
    }

    // the advert made before android 17 let us on the wi-fi never went out, so it's made again with the same code
    @Synchronized
    fun advertiseAgain() {
        if (!server.isRunning) return
        advert?.close()
        advertise(LinkIdentity.name(context), server.port)
    }

    private fun advertise(name: String, port: Int) {
        advert = LinkAdvert(
            context,
            LinkWire.PairServiceType,
            "SekaiTune $name",
            port,
            mapOf(
                LinkWire.AttrId to LinkIdentity.id(context),
                LinkWire.AttrName to name,
                LinkWire.AttrKind to DeviceKind.of(context).wire,
            ),
        )
    }

    @Synchronized
    fun close() {
        advert?.close()
        advert = null
        server.stop()
        _code.value = null
    }

    private fun newCode() {
        wrongTries = 0
        _code.value = PairingCode(
            code = (random.nextInt(900_000) + 100_000).toString(),
            deviceName = LinkIdentity.name(context),
            address = localAddress(),
        )
    }

    // this device's address on the wi-fi, for a phone on a network that hides devices from each other
    private fun localAddress(): String? = runCatching {
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        val properties = connectivity.getLinkProperties(connectivity.activeNetwork) ?: return null
        properties.linkAddresses.map { it.address }.firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }.getOrNull()

    private fun route(request: LinkRequest): LinkResponse = when {
        request.path == "/pair/hello" -> LinkResponse.json(
            JSONObject()
                .put("id", LinkIdentity.id(context))
                .put("name", LinkIdentity.name(context))
                .put("kind", DeviceKind.of(context).wire)
                .put("protocol", LinkWire.ProtocolVersion)
                .toString(),
        )
        request.path == "/pair" && request.method == "POST" -> pair(request)
        else -> LinkResponse.status(404)
    }

    private fun pair(request: LinkRequest): LinkResponse {
        val body = runCatching { JSONObject(request.bodyText()) }.getOrNull() ?: return LinkResponse.status(400)
        val expected = _code.value?.code ?: return LinkResponse.status(409, "not pairing")
        if (!sameCode(body.optString("code"), expected)) {
            // guessing is cut short: a few wrong codes and the screen shows a new one
            wrongTries++
            if (wrongTries >= MaxWrongTries) newCode()
            return LinkResponse.status(403, "wrong code")
        }
        val reply = onPaired(body, request.remoteHost, expected)
        // a used code is never good twice
        if (reply.status == 200) newCode()
        return reply
    }

    private fun sameCode(given: String, expected: String): Boolean =
        MessageDigest.isEqual(given.trim().toByteArray(), expected.toByteArray())

    private companion object {
        const val MaxWrongTries = 5
    }
}

