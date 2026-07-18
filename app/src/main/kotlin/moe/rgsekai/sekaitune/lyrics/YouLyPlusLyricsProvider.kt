/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.lyrics

import android.content.Context
import android.util.Log
import moe.rgsekai.sekaitune.constants.EnableYouLyPlusLyricsKey
import moe.rgsekai.sekaitune.utils.GlobalLog
import moe.rgsekai.sekaitune.utils.dataStore
import moe.rgsekai.sekaitune.utils.get
import moe.rgsekai.sekaitune.youlyplus.YouLyPlus

object YouLyPlusLyricsProvider : LyricsProvider {
    init {
        YouLyPlus.logger = { message ->
            GlobalLog.append(Log.INFO, "YouLyPlus", message)
        }
    }

    override val name = "YouLyPlus"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableYouLyPlusLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> =
        YouLyPlus.getLyrics(
            title = title,
            artist = artist,
            album = album,
            durationSeconds = duration,
        )

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        YouLyPlus.getAllLyrics(
            title = title,
            artist = artist,
            album = album,
            durationSeconds = duration,
            callback = callback,
        )
    }
}




