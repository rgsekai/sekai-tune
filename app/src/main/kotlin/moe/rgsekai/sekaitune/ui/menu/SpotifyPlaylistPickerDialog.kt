/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.menu

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.spotify.Spotify
import moe.rgsekai.sekaitune.spotify.SpotifyPlaybackResolver
import moe.rgsekai.sekaitune.spotify.models.SpotifyPlaylist

object SpotifyPlaylistsCache {
    private const val TTL_MS = 5 * 60 * 1000L // 5 minutes TTL
    private var cachedPlaylists: List<SpotifyPlaylist>? = null
    private var lastFetchTime = 0L

    fun get(): List<SpotifyPlaylist>? {
        val now = System.currentTimeMillis()
        return if (cachedPlaylists != null && now - lastFetchTime < TTL_MS) {
            cachedPlaylists
        } else {
            null
        }
    }

    fun put(playlists: List<SpotifyPlaylist>) {
        cachedPlaylists = playlists
        lastFetchTime = System.currentTimeMillis()
    }

    fun invalidate() {
        cachedPlaylists = null
        lastFetchTime = 0L
    }
}

@Composable
fun SpotifyPlaylistPickerDialog(
    onSelect: (SpotifyPlaylist) -> Unit,
    addingPlaylistId: String? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var playlists by remember { mutableStateOf<List<SpotifyPlaylist>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val repo = moe.rgsekai.sekaitune.spotify.SpotifyLibraryRepository(context.applicationContext)
        val memoryCached = SpotifyPlaylistsCache.get()
        if (memoryCached != null && memoryCached.isNotEmpty()) {
            playlists = memoryCached
            isLoading = false
            return@LaunchedEffect
        }

        val diskCached = withContext(Dispatchers.IO) { repo.getCachedPlaylists() }
        if (diskCached.isNotEmpty()) {
            playlists = diskCached
            SpotifyPlaylistsCache.put(diskCached)
            isLoading = false
        } else {
            isLoading = true
        }

        error = null
        withContext(Dispatchers.IO) {
            runCatching {
                repo.ensureAuthenticated()
                repo.refreshPlaylists()
            }.onSuccess { loaded ->
                withContext(Dispatchers.Main) {
                    playlists = loaded
                    SpotifyPlaylistsCache.put(loaded)
                    isLoading = false
                }
            }.onFailure { err ->
                withContext(Dispatchers.Main) {
                    if (playlists.isEmpty()) {
                        error = err.message ?: context.getString(R.string.spotify_add_to_playlist_failed)
                    }
                    isLoading = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (addingPlaylistId == null) onDismiss()
        },
        title = { Text(stringResource(R.string.spotify_add_to_playlist)) },
        text = {
            when {
                isLoading -> {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                error != null -> {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                playlists.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.spotify_no_playlists),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            val isThisRowAdding = addingPlaylistId == playlist.id
                            val isAnyRowAdding = addingPlaylistId != null

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = !isAnyRowAdding) {
                                            onSelect(playlist)
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                            ) {
                                val imageUrl = playlist.images.firstOrNull()?.url
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val subtitle =
                                        buildString {
                                            playlist.owner?.displayName?.takeIf { it.isNotBlank() }?.let { append(it) }
                                            val totalCount = playlist.tracks?.total ?: 0
                                            if (totalCount > 0) {
                                                if (isNotEmpty()) append(" • ")
                                                append("$totalCount tracks")
                                            }
                                        }
                                    if (subtitle.isNotBlank()) {
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (isThisRowAdding) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = addingPlaylistId == null,
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@androidx.compose.runtime.Immutable
data class SpotifyResolvableTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Int = -1,
    val spotifyUri: String? = null,
)

/**
 * Consolidated, shared composable managing the full "Add to Spotify playlist" flow:
 * 1. Concurrently resolves YTM songs to Spotify URIs (using MAX_CONCURRENT_RESOLUTIONS = 8, LRU cache & threshold = 0.35).
 * 2. Shows loading state with progress during batch resolution.
 * 3. Shows SpotifyPlaylistPickerDialog on resolution.
 * 4. Checks for duplicate tracks in the target playlist to avoid duplicate adds.
 * 5. Calls Spotify.addTracksToPlaylist with all resolved URIs in a single batched API call.
 * 6. Notifies the user with clear summary / partial success toasts.
 */
@Composable
fun AddToSpotifyPlaylistFlow(
    showDialog: Boolean,
    tracks: List<SpotifyResolvableTrack>,
    onDismiss: () -> Unit,
) {
    if (!showDialog || tracks.isEmpty()) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var resolvedUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var unresolvedCount by remember { mutableStateOf(0) }
    var isResolving by remember { mutableStateOf(true) }
    var resolveError by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var resolvedProgress by remember { mutableStateOf(0 to tracks.size) }

    LaunchedEffect(showDialog, tracks) {
        val allPreResolved = tracks.all { !it.spotifyUri.isNullOrBlank() }
        if (allPreResolved) {
            resolvedUris = tracks.mapNotNull { it.spotifyUri }
            unresolvedCount = 0
            showPicker = true
            isResolving = false
            return@LaunchedEffect
        }

        isResolving = true
        resolveError = null
        val total = tracks.size
        val resolvedList = mutableListOf<String>()
        var currentUnresolved = 0

        withContext(Dispatchers.IO) {
            runCatching {
                moe.rgsekai.sekaitune.spotify.SpotifyLibraryRepository(context.applicationContext).ensureAuthenticated()
            }
            // Concurrent chunked resolution (MAX_CONCURRENT_RESOLUTIONS = 8)
            tracks.chunked(8).forEach { chunk ->
                val chunkResults: List<String?> =
                    coroutineScope {
                        chunk.map { track ->
                            async {
                                if (!track.spotifyUri.isNullOrBlank()) {
                                    track.spotifyUri
                                } else {
                                    SpotifyPlaybackResolver
                                        .resolveToSpotifyUri(
                                            youtubeId = track.id,
                                            title = track.title,
                                            artist = track.artist,
                                            durationSec = track.durationSec,
                                        ).getOrNull()
                                }
                            }
                        }.awaitAll()
                    }

                chunkResults.forEach { uri ->
                    if (uri != null) {
                        resolvedList.add(uri)
                    } else {
                        currentUnresolved++
                    }
                }
                withContext(Dispatchers.Main) {
                    resolvedProgress = (resolvedList.size + currentUnresolved) to total
                }
            }
        }

        if (resolvedList.isEmpty()) {
            isResolving = false
            resolveError = "not_found"
        } else {
            resolvedUris = resolvedList
            unresolvedCount = currentUnresolved
            isResolving = false
            showPicker = true
        }
    }

    when {
        isResolving -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.spotify_add_to_playlist)) },
                text = {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            val loadingText =
                                if (tracks.size > 1) {
                                    stringResource(
                                        R.string.spotify_resolving_batch,
                                        resolvedProgress.first,
                                        resolvedProgress.second,
                                    )
                                } else {
                                    stringResource(R.string.spotify_searching)
                                }
                            Text(
                                text = loadingText,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }

        resolveError != null -> {
            LaunchedEffect(resolveError) {
                val message =
                    when (resolveError) {
                        "timeout" -> context.getString(R.string.spotify_resolution_timeout)
                        "not_found" -> context.getString(R.string.spotify_track_not_found_on_spotify)
                        else -> context.getString(R.string.spotify_add_to_playlist_failed)
                    }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }

        showPicker && resolvedUris.isNotEmpty() -> {
            var addingPlaylistId by remember { mutableStateOf<String?>(null) }

            SpotifyPlaylistPickerDialog(
                addingPlaylistId = addingPlaylistId,
                onSelect = { selectedPlaylist ->
                    addingPlaylistId = selectedPlaylist.id
                    coroutineScope.launch(Dispatchers.IO) {
                        val repo = moe.rgsekai.sekaitune.spotify.SpotifyLibraryRepository(context.applicationContext)
                        runCatching {
                            repo.ensureAuthenticated()
                        }.onFailure { err ->
                            withContext(Dispatchers.Main) {
                                addingPlaylistId = null
                                Toast.makeText(
                                    context,
                                    err.message ?: context.getString(R.string.spotify_add_to_playlist_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            return@launch
                        }
                        // Check for duplicate tracks in the target playlist
                        val existingTracks =
                            Spotify
                                .playlistTracks(selectedPlaylist.id, limit = 100)
                                .getOrNull()
                                ?.items
                                .orEmpty()
                        val existingUris = existingTracks.mapNotNull { it.track?.uri }.toSet()
                        val existingIds = existingTracks.mapNotNull { it.track?.id }.toSet()

                        val urisToAdd =
                            resolvedUris.filter { uri ->
                                val rawId = uri.substringAfterLast(":")
                                uri !in existingUris && rawId !in existingIds
                            }
                        val duplicateCount = resolvedUris.size - urisToAdd.size

                        if (urisToAdd.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                addingPlaylistId = null
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.spotify_track_already_in_playlist,
                                        selectedPlaylist.name,
                                    ),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            return@launch
                        }

                        // Add all resolved, non-duplicate URIs in a single batched API call
                        val result = Spotify.addTracksToPlaylist(selectedPlaylist.id, urisToAdd)
                        result
                            .onSuccess {
                                SpotifyPlaylistsCache.invalidate()
                                withContext(Dispatchers.Main) {
                                    addingPlaylistId = null
                                    val totalSelected = tracks.size
                                    val addedCount = urisToAdd.size
                                    val notFoundCount = unresolvedCount

                                    val message =
                                        when {
                                            totalSelected == 1 -> {
                                                context.getString(
                                                    R.string.spotify_track_added_to_playlist,
                                                    selectedPlaylist.name,
                                                )
                                            }

                                            notFoundCount > 0 -> {
                                                context.getString(
                                                    R.string.spotify_partial_add_to_playlist,
                                                    addedCount,
                                                    totalSelected,
                                                    selectedPlaylist.name,
                                                    notFoundCount,
                                                )
                                            }

                                            duplicateCount > 0 -> {
                                                context.getString(
                                                    R.string.spotify_tracks_added_with_duplicates,
                                                    addedCount,
                                                    selectedPlaylist.name,
                                                    duplicateCount,
                                                )
                                            }

                                            else -> {
                                                context.getString(
                                                    R.string.spotify_tracks_added_to_playlist,
                                                    addedCount,
                                                    selectedPlaylist.name,
                                                )
                                            }
                                        }

                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    onDismiss()
                                }
                            }.onFailure { err ->
                                withContext(Dispatchers.Main) {
                                    addingPlaylistId = null
                                    val errorMsg =
                                        err.message ?: context.getString(R.string.spotify_add_to_playlist_failed)
                                    Toast.makeText(
                                        context,
                                        errorMsg,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                    }
                },
                onDismiss = onDismiss,
            )
        }
    }
}

/**
 * Single-song convenience overload for AddToSpotifyPlaylistFlow.
 */
@Composable
fun AddToSpotifyPlaylistFlow(
    showDialog: Boolean,
    youtubeId: String,
    title: String,
    artist: String,
    durationSec: Int = -1,
    spotifyUri: String? = null,
    onDismiss: () -> Unit,
) = AddToSpotifyPlaylistFlow(
    showDialog = showDialog,
    tracks =
        listOf(
            SpotifyResolvableTrack(
                id = youtubeId,
                title = title,
                artist = artist,
                durationSec = durationSec,
                spotifyUri = spotifyUri,
            ),
        ),
    onDismiss = onDismiss,
)
