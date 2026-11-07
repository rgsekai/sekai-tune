/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.scrobble

import androidx.datastore.preferences.core.Preferences
import moe.rgsekai.sekaitune.BuildConfig
import moe.rgsekai.sekaitune.constants.LastFmEnabledKey
import moe.rgsekai.sekaitune.constants.LastFmNowPlayingKey
import moe.rgsekai.sekaitune.constants.LastFmPrimaryArtistOnlyKey
import moe.rgsekai.sekaitune.constants.LastFmScrobbleKey
import moe.rgsekai.sekaitune.constants.LastFmSessionKey
import moe.rgsekai.sekaitune.constants.LastFmUsernameKey
import moe.rgsekai.sekaitune.constants.LibreFmEnabledKey
import moe.rgsekai.sekaitune.constants.LibreFmNowPlayingKey
import moe.rgsekai.sekaitune.constants.LibreFmPrimaryArtistOnlyKey
import moe.rgsekai.sekaitune.constants.LibreFmScrobbleKey
import moe.rgsekai.sekaitune.constants.LibreFmSessionKey
import moe.rgsekai.sekaitune.constants.LibreFmUsernameKey

enum class ScrobbleProvider(
    val id: String,
    val title: String,
    val queueFileName: String,
    val sessionKeyPref: Preferences.Key<String>,
    val usernamePref: Preferences.Key<String>,
    val enabledPref: Preferences.Key<Boolean>,
    val scrobblePref: Preferences.Key<Boolean>,
    val nowPlayingPref: Preferences.Key<Boolean>,
    val primaryArtistOnlyPref: Preferences.Key<Boolean>,
    val registerUrl: String,
    val client: AudioscrobblerClient,
) {
    LASTFM(
        id = "lastfm",
        title = "Last.fm",
        queueFileName = "lastfm_scrobble_queue.json",
        sessionKeyPref = LastFmSessionKey,
        usernamePref = LastFmUsernameKey,
        enabledPref = LastFmEnabledKey,
        scrobblePref = LastFmScrobbleKey,
        nowPlayingPref = LastFmNowPlayingKey,
        primaryArtistOnlyPref = LastFmPrimaryArtistOnlyKey,
        registerUrl = "https://www.last.fm/join",
        client = AudioscrobblerClient(
            apiRoot = "https://ws.audioscrobbler.com/2.0/",
            apiKey = BuildConfig.LASTFM_API_KEY,
            apiSecret = BuildConfig.LASTFM_SECRET,
            useTokenAuth = false,
        ),
    ),
    LIBREFM(
        id = "librefm",
        title = "Libre.fm",
        queueFileName = "librefm_scrobble_queue.json",
        sessionKeyPref = LibreFmSessionKey,
        usernamePref = LibreFmUsernameKey,
        enabledPref = LibreFmEnabledKey,
        scrobblePref = LibreFmScrobbleKey,
        nowPlayingPref = LibreFmNowPlayingKey,
        primaryArtistOnlyPref = LibreFmPrimaryArtistOnlyKey,
        registerUrl = "https://libre.fm/register.php",
        client = AudioscrobblerClient(
            apiRoot = "https://libre.fm/2.0/",
            apiKey = BuildConfig.LIBREFM_API_KEY,
            apiSecret = BuildConfig.LIBREFM_SECRET,
            useTokenAuth = true,
        ),
    ),
}

