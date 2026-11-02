package moe.rgsekai.sekaitune.shared.data.canvas

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CanvasProtoTest {

    @Test
    fun requestMatchesProtobufWireBytes() {
        // CanvasRequest { tracks = [Track { track_uri = "spotify:track:abc" }] } as protoc would emit it.
        val uri = "spotify:track:abc"
        val expected = byteArrayOf(0x0A, 0x13, 0x0A, 0x11) + uri.encodeToByteArray()
        assertContentEquals(expected, SpotifyCanvas.encodeCanvasRequest(uri))
    }

    @Test
    fun responseReadsIdUrlAndTrackUriAndSkipsOtherFields() {
        val canvas = CanvasProto.string(1, "id1") +
            CanvasProto.string(2, "https://canvaz.scdn.co/x.cnvs.mp4") +
            byteArrayOf(0x18, 0x01) + // field 3, varint: skipped
            CanvasProto.string(5, "spotify:track:abc") +
            byteArrayOf(0x3D, 1, 2, 3, 4) // field 7, fixed32: skipped
        val response = CanvasProto.field(1, canvas) + CanvasProto.field(1, CanvasProto.string(1, "no url"))

        val hits = SpotifyCanvas.decodeCanvasResponse(response)
        assertEquals(1, hits.size) // the canvas without a url is dropped
        assertEquals("id1", hits[0].id)
        assertEquals("https://canvaz.scdn.co/x.cnvs.mp4", hits[0].url)
        assertEquals("spotify:track:abc", hits[0].trackUri)
    }

    @Test
    fun longPayloadUsesMultiByteLength() {
        val long = "a".repeat(300)
        val back = CanvasProto.fields(CanvasProto.string(2, long)).single()
        assertEquals(2, back.first)
        assertEquals(long, back.second.decodeToString())
    }

    @Test
    fun malformedResponseIsEmptyNotAThrow() {
        assertEquals(emptyList(), SpotifyCanvas.decodeCanvasResponse(byteArrayOf(0x0A, 0x7F, 0x01)))
    }

    @Test
    fun harvestSkipsAnonymousAndReadsLoggedIn() {
        assertNull(SpotifyToken.parseHarvest("""{"accessToken":"t","isAnonymous":true}"""))
        assertNull(SpotifyToken.parseHarvest("not json"))
        val ok = assertNotNull(
            SpotifyToken.parseHarvest("""{"accessToken":"t","isAnonymous":false,"clientId":"c","accessTokenExpirationTimestampMs":99999999999999}"""),
        )
        assertEquals("t", ok.token)
        assertEquals("c", ok.clientId)
        assertEquals(99999999999999L, ok.expiresAt)
    }
}
