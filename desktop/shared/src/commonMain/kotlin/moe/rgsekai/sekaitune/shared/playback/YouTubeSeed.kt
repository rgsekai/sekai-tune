package moe.rgsekai.sekaitune.shared.playback

import moe.rgsekai.sekaitune.shared.data.YtMusicRepository
import moe.rgsekai.sekaitune.shared.data.model.SearchFilter
import moe.rgsekai.sekaitune.shared.data.model.SearchResult
import moe.rgsekai.sekaitune.shared.data.model.Song
import moe.rgsekai.sekaitune.shared.data.sources.SourceRegistry
import moe.rgsekai.sekaitune.shared.data.sources.TrackMatcher

/**
 * Finds the YouTube id that should seed AutoPlay for a song. Module tracks do not
 * carry YouTube ids, so they are matched on YouTube before the radio request.
 */
suspend fun youtubeSeedFor(song: Song): String? {
    if (SourceRegistry.parseTrackKey(song.videoId) == null) return song.videoId
    val target = TrackMatcher.targetOf(song)
    val query = TrackMatcher.queries(target).firstOrNull() ?: return null
    return YtMusicRepository.search(query, SearchFilter.SONGS)
        .getOrNull()
        ?.filterIsInstance<SearchResult.Track>()
        ?.map { it.song }
        ?.let { TrackMatcher.best(it, target) }
        ?.videoId
}
