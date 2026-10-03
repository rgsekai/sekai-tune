package moe.rgsekai.sekaitune.playback

import moe.rgsekai.sekaitune.db.entities.ArtistEntity
import moe.rgsekai.sekaitune.db.entities.SongEntity
import moe.rgsekai.sekaitune.spotify.SpotifyMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ResolveUploadedCatalogMatchUseCaseTest {

    @Test
    fun `verify Clarification 4 math - perfect title and artist with 5s duration difference yields 0_96`() {
        // (TitleScore * 0.45) + (ArtistScore * 0.35) + (DurationScore * 0.20)
        // With Title = 1.0, Artist = 1.0, diff = 5s -> DurationScore = 0.8
        // Score = 1.0 * 0.45 + 1.0 * 0.35 + 0.8 * 0.20 = 0.45 + 0.35 + 0.16 = 0.96
        val precomputed = SpotifyMapper.precompute(
            title = "Blinding Lights",
            artist = "The Weeknd",
            durationMs = 200 * 1000,
        )

        val score = SpotifyMapper.matchScorePrecomputed(
            precomputed = precomputed,
            candidateTitle = "Blinding Lights",
            candidateArtist = "The Weeknd",
            candidateDurationSec = 205, // 5s difference
        )

        assertEquals(0.96, score, 0.001)
        assertTrue("Score 0.96 must clear high confidence threshold 0.70", score >= 0.70)
    }

    @Test
    fun `verify real song title and artist matching variations`() {
        // Test real world variations:
        // 1. "Shape of You" by "Ed Sheeran" vs candidate "Shape of You" by "Ed Sheeran" with 2s diff
        val precomputed1 = SpotifyMapper.precompute(
            title = "Shape of You",
            artist = "Ed Sheeran",
            durationMs = 233 * 1000,
        )
        val score1 = SpotifyMapper.matchScorePrecomputed(precomputed1, "Shape of You", "Ed Sheeran", 235)
        assertEquals(1.0, score1, 0.001) // 1.0 * 0.45 + 1.0 * 0.35 + 1.0 * 0.20 = 1.0 (High confidence >= 0.70)
        assertTrue(score1 >= 0.70)

        // 2. Title with extra remaster/audio tags in candidate
        val precomputed2 = SpotifyMapper.precompute(
            title = "Bohemian Rhapsody",
            artist = "Queen",
            durationMs = 354 * 1000,
        )
        val score2 = SpotifyMapper.matchScorePrecomputed(precomputed2, "Bohemian Rhapsody (Official Audio)", "Queen", 355)
        assertTrue("Normalized matching should clear >= 0.70 high confidence tier", score2 >= 0.70)

        // 3. Medium confidence test: same title, slightly different artist featuring or longer duration diff (8s)
        val precomputed3 = SpotifyMapper.precompute(
            title = "Stay",
            artist = "The Kid LAROI",
            durationMs = 141 * 1000,
        )
        val score3 = SpotifyMapper.matchScorePrecomputed(precomputed3, "Stay", "The Kid LAROI & Justin Bieber", 149)
        assertTrue("Medium confidence match should be between 0.40 and 0.90", score3 in 0.40..0.90)
    }

    @Test
    fun `verify scoring curve for duration differences`() {
        val precomputed = SpotifyMapper.precompute(
            title = "Song Title",
            artist = "Artist Name",
            durationMs = 180 * 1000,
        )

        // diff <= 2s -> 1.0 duration score
        val score2s = SpotifyMapper.matchScorePrecomputed(precomputed, "Song Title", "Artist Name", 182)
        assertEquals(1.0, score2s, 0.001)

        // diff <= 5s -> 0.8 duration score
        val score5s = SpotifyMapper.matchScorePrecomputed(precomputed, "Song Title", "Artist Name", 185)
        assertEquals(0.96, score5s, 0.001)

        // diff <= 10s -> 0.5 duration score
        val score10s = SpotifyMapper.matchScorePrecomputed(precomputed, "Song Title", "Artist Name", 190)
        assertEquals(0.90, score10s, 0.001)

        // diff <= 30s -> 0.2 duration score
        val score30s = SpotifyMapper.matchScorePrecomputed(precomputed, "Song Title", "Artist Name", 210)
        assertEquals(0.84, score30s, 0.001)

        // diff > 30s -> 0.0 duration score
        val scoreOver30s = SpotifyMapper.matchScorePrecomputed(precomputed, "Song Title", "Artist Name", 240)
        assertEquals(0.80, scoreOver30s, 0.001)
    }
}
