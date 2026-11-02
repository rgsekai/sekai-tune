package moe.rgsekai.sekaitune.shared.data

import moe.rgsekai.sekaitune.shared.data.model.Song
import moe.rgsekai.sekaitune.shared.data.settings.LocalMusicSort

/**
 * Whether this track is a hit for a query typed into a page's filter box — or
 * into the Search tab with its Library source picked, which has to find the
 * same tracks a folder would.
 */
fun Song.matchesSearch(query: String): Boolean =
    title.contains(query, ignoreCase = true) ||
        artist.contains(query, ignoreCase = true) ||
        albumName?.contains(query, ignoreCase = true) == true

/** A device folder's running order, per the sort its top bar menu picked. */
fun List<Song>.sortedForLibrary(order: LocalMusicSort): List<Song> = when (order) {
    LocalMusicSort.TITLE_ASC -> sortedByTitle()
    LocalMusicSort.TITLE_DESC -> sortedWith(compareByDescending<Song> { it.title.lowercase() })
    LocalMusicSort.DATE_ADDED -> sortedWith(
        compareByDescending<Song> { it.localDateAddedSeconds ?: Long.MIN_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )
    LocalMusicSort.DATE_MODIFIED -> sortedWith(
        compareByDescending<Song> { it.localDateModifiedSeconds ?: Long.MIN_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
    )
}

fun List<Song>.sortedByTitle(): List<Song> =
    sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

/**
 * Whether a track list is one album's own tracks rather than an artist's mixed
 * rows: every song names the same album and at least one carries a tag number.
 * Only then does a numbered row show the tag's number rather than its place.
 */
fun List<Song>.useTrackTags(): Boolean {
    if (isEmpty() || none { it.trackNumber != null }) return false
    val album = first().albumName ?: return false
    return all { it.albumName == album }
}
