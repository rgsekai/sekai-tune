package moe.rgsekai.sekaitune.car

import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import androidx.media3.common.util.UnstableApi
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.link.phone.PhoneLinkHost
import moe.rgsekai.sekaitune.link.phone.PhonePairing
import moe.rgsekai.sekaitune.lyrics.LyricsHelper
import moe.rgsekai.sekaitune.playback.DownloadUtil
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// Sekai Tune's android auto screens, drawn by the car from Google's media templates
@UnstableApi
@AndroidEntryPoint
class SekaiTuneCarAppService : CarAppService() {
    @Inject lateinit var database: MusicDatabase
    @Inject lateinit var lyricsHelper: LyricsHelper
    @Inject lateinit var pairing: PhonePairing
    @Inject lateinit var link: PhoneLinkHost
    @Inject lateinit var downloadUtil: DownloadUtil

    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext).addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build()
        }

    override fun onCreateSession(sessionInfo: SessionInfo): Session =
        CarSession(database, lyricsHelper, pairing, link, downloadUtil)
}

