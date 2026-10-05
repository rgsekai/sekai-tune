/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.innertube.pages

import moe.rgsekai.sekaitune.innertube.models.AlbumItem
import moe.rgsekai.sekaitune.innertube.models.AlbumReleaseType
import moe.rgsekai.sekaitune.innertube.models.Artist
import moe.rgsekai.sekaitune.innertube.models.MusicTwoRowItemRenderer
import moe.rgsekai.sekaitune.innertube.models.oddElements
import moe.rgsekai.sekaitune.innertube.models.splitBySeparator

object NewReleaseAlbumPage {
    fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? {
        val browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null
        val title = renderer.title.runs?.firstOrNull()?.text ?: return null
        val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null

        val playNavigationEndpoint =
            renderer.thumbnailOverlay
                ?.musicItemThumbnailOverlayRenderer
                ?.content
                ?.musicPlayButtonRenderer
                ?.playNavigationEndpoint

        val playlistId =
            playNavigationEndpoint?.anyWatchEndpoint?.playlistId
                ?: renderer.navigationEndpoint.anyWatchEndpoint?.playlistId
                ?: browseId.replace("MPREb_", "OLAK5uy_")

        val subtitleRuns = renderer.subtitle?.runs.orEmpty()
        val subtitleGroups = subtitleRuns.splitBySeparator()

        var releaseType = AlbumReleaseType.ALBUM
        var artists: List<Artist>? = null
        var year: Int? = subtitleRuns.lastOrNull()?.text?.toIntOrNull()

        when {
            subtitleGroups.size >= 3 -> {
                releaseType = AlbumReleaseType.fromLabel(subtitleGroups[0].joinToString("") { it.text })
                artists =
                    subtitleGroups[1].oddElements().map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId,
                        )
                    }
                year = subtitleGroups.last().firstOrNull()?.text?.toIntOrNull() ?: year
            }
            subtitleGroups.size == 2 -> {
                val group0Text = subtitleGroups[0].joinToString("") { it.text }
                val group1Year = subtitleGroups[1].firstOrNull()?.text?.toIntOrNull()
                val inferredType = AlbumReleaseType.fromLabel(group0Text)

                if (group1Year != null) {
                    artists =
                        subtitleGroups[0].oddElements().map {
                            Artist(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId,
                            )
                        }
                    year = group1Year
                    releaseType = AlbumReleaseType.ALBUM
                } else if (inferredType != AlbumReleaseType.ALBUM || group0Text.equals("album", ignoreCase = true)) {
                    releaseType = inferredType
                    artists =
                        subtitleGroups[1].oddElements().map {
                            Artist(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId,
                            )
                        }
                } else {
                    val group1HasArtist = subtitleGroups[1].any { it.navigationEndpoint?.browseEndpoint?.browseId?.startsWith("UC") == true }
                    val group0HasArtist = subtitleGroups[0].any { it.navigationEndpoint?.browseEndpoint?.browseId?.startsWith("UC") == true }
                    if (group1HasArtist && !group0HasArtist) {
                        releaseType = inferredType
                        artists =
                            subtitleGroups[1].oddElements().map {
                                Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId)
                            }
                    } else {
                        artists =
                            subtitleGroups[0].oddElements().map {
                                Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId)
                            }
                    }
                }
            }
            subtitleGroups.size == 1 -> {
                artists =
                    subtitleGroups[0].oddElements().map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId,
                        )
                    }
            }
            else -> {
                artists = emptyList()
            }
        }

        val explicit =
            renderer.subtitleBadges?.find {
                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
            } != null

        return AlbumItem(
            browseId = browseId,
            playlistId = playlistId,
            title = title,
            artists = artists,
            year = year,
            releaseType = releaseType,
            thumbnail = thumbnail,
            explicit = explicit,
        )
    }
}




