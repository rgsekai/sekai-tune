package moe.rgsekai.sekaitune.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import moe.rgsekai.sekaitune.desktop.SekaiModelAdapters.toLegacyPlayerSong
import moe.rgsekai.sekaitune.desktop.SekaiModelAdapters.toSearchResult
import moe.rgsekai.sekaitune.innertube.models.Artist
import moe.rgsekai.sekaitune.innertube.models.SongItem
import moe.rgsekai.sekaitune.shared.data.model.SearchResult

class SekaiModelAdaptersTest {

    @Test
    fun testSongItemMapping() {
        val songItem = SongItem(
            id = "test_vid_123",
            title = "Kesariya",
            artists = listOf(Artist(name = "Arijit Singh", id = "artist_id_1")),
            album = null,
            duration = 268,
            thumbnail = "https://example.com/art.jpg",
            explicit = false,
        )

        val playerSong = songItem.toLegacyPlayerSong()
        assertEquals("test_vid_123", playerSong.videoId)
        assertEquals("Kesariya", playerSong.title)
        assertEquals("Arijit Singh", playerSong.artist)
        assertEquals("4:28", playerSong.durationText)
        assertEquals("https://example.com/art.jpg", playerSong.thumbnailUrl)
        assertEquals("artist_id_1", playerSong.artistId)

        val searchResult = songItem.toSearchResult()
        assertNotNull(searchResult)
        assertEquals(true, searchResult is SearchResult.Track)
        assertEquals("Kesariya", (searchResult as SearchResult.Track).song.title)
    }

    @Test
    fun testFormatDuration() {
        assertEquals("0:05", SekaiModelAdapters.formatDuration(5))
        assertEquals("3:45", SekaiModelAdapters.formatDuration(225))
        assertEquals("12:00", SekaiModelAdapters.formatDuration(720))
        assertEquals(null, SekaiModelAdapters.formatDuration(null))
        assertEquals(null, SekaiModelAdapters.formatDuration(0))
    }
}
