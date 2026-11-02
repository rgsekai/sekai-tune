package moe.rgsekai.sekaitune.shared.data.innertube

import moe.rgsekai.sekaitune.shared.data.MonotonicClock
import moe.rgsekai.sekaitune.shared.data.NerdStats
import moe.rgsekai.sekaitune.shared.data.TrackLog
import moe.rgsekai.sekaitune.shared.platform.ConcurrentMap
import moe.rgsekai.sekaitune.shared.platform.HttpRequest
import moe.rgsekai.sekaitune.shared.platform.ioDispatcher
import io.ktor.http.Url
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okio.IOException
import kotlin.concurrent.Volatile

/**
 * Turns a videoId into a URL ExoPlayer can actually stream.
 *
 *  1. **InnerTubeX first.** Its live-benchmarked client catalog, cipher tiers
 *     and PoTokens decide which identity asks and unlock what it answers. See
 *     [InnerTubeXResolver].
 *
 *  2. **NewPipe as the failsafe.** It re-derives everything from the watch
 *     page, which is several hundred kilobytes of HTML Google rate-shapes under
 *     load, so it is kept off the hot path and reached only when InnerTubeX
 *     finds nothing. See [newPipeStream].
 *
 *  3. **Whether the URL is real.** A URL can be dead on arrival for reasons no
 *     amount of care predicts, so nothing is handed to the player, or cached,
 *     until bytes have been fetched from it. See [probe].
 */
object StreamResolver {
    /** The bitrate ceiling in force — the phone's quality setting for the connection it is on. */
    @Volatile
    var maxKbps: () -> Int = { Int.MAX_VALUE }


    private const val TAG = "SekaiTune"

        /**
     * @return a directly streamable URL that has been proven to serve bytes,
     *   or throws with a reason worth showing.
     *
     * Results are held briefly — see [recent]. Resolving is the slow part of
     * starting a track, and ExoPlayer asks again for every re-open: each seek
     * outside the buffer, and each range the cache fills in.
     */
    suspend fun resolve(videoId: String): String {
        StreamPlatform.init()

        recent[videoId]
            ?.takeIf { MonotonicClock.nowMs() - it.at < URL_TTL_MS }
            ?.let { return it.url }

        // A verdict, not a failure: asking again cannot change the answer, so
        // every caller after the first is told so without a request being sent.
        // See [rememberUnplayable] for why this is the fix for "stuck loading".
        unplayableReason(videoId)?.let { throw PermanentlyUnplayableException(it) }

        val stream = coalescedResolve(videoId)

        // The container carries no bitrate field, so this is the only place the
        // real figure is ever known.
        NerdStats.onStreamPicked(videoId, stream.kbps)
        stream.loudnessDb?.let { loudness[videoId] = it }
        remember(videoId, stream.url)
        return stream.url
    }

    /** Per-track loudness, read once and kept for as long as the process runs. */
    private val loudness = ConcurrentMap<String, Double>()

    /**
     * YouTube's own normalization figure for [videoId], or null when it has
     * never resolved or never carried one.
     *
     * Populated by [resolve] the first time a track's stream is asked for —
     * which happens for every YouTube-queued track whether or not another
     * source ends up serving its bytes, since the YouTube walk always runs
     * alongside a substitute lookup rather than only when one fails. So a
     * track substituted to generic source or an addon still carries the figure its
     * YouTube counterpart resolved.
     */
    fun loudnessDbFor(videoId: String): Double? = loudness[videoId]

    /**
     * A track this app cannot play, for a reason that will read the same in ten
     * seconds — an age gate no session gets past, a takedown, a region block.
     *
     * Its own type because everything above the resolver has to be able to tell
     * it apart from a failure worth retrying, and the layers in between are
     * ExoPlayer's: a load error carries whatever exception it was given and
     * nothing else, so the distinction has to travel in the type. See
     * [PlaybackService][moe.rgsekai.sekaitune.shared.playback.PlaybackService]'s load-error
     * policy and `recoverFrom`.
     */
    class PermanentlyUnplayableException(reason: String) : IOException(reason)

    /**
     * Tracks that have already failed for a reason retrying cannot fix, and
     * until when.
     *
     * This is the single change that turns the observed failure — a track that
     * sits in BUFFERING for minutes on end, hammering youtubei — back into a
     * failure that happens once. Nothing above this object retries *less* than
     * three deep: ExoPlayer's own load-error policy retries the source, this
     * service's `recoverFrom` retries the player, and read-ahead resolves the
     * same track again on its own schedule. Against a permanent refusal every
     * one of those is a full client walk plus a triple extraction — measured in
     * the report at roughly twenty-seven walks and fifty youtubei requests in a
     * 2m41s window, for a track whose answer was settled by the first one.
     *
     * Entries expire rather than being permanent, because the reasons behind
     * them do: an age gate stops mattering the moment the listener signs in
     * (see [forgetUnplayable], called from the login flow), and Google's region
     * and bot verdicts are measured in hours, not sessions. Ten minutes is the
     * same budget [STAND_DOWN_MS] uses, for the same reason — long enough that
     * the storm cannot re-form, short enough that a listener who fixes the
     * cause does not have to restart the app.
     */
    private val unplayable = ConcurrentMap<String, Verdict>()

    private class Verdict(val reason: String, val at: Long)

    private fun unplayableReason(videoId: String): String? {
        val entry = unplayable[videoId] ?: return null
        if (MonotonicClock.nowMs() - entry.at < UNPLAYABLE_TTL_MS) return entry.reason
        unplayable.remove(videoId)
        return null
    }

    private fun rememberUnplayable(videoId: String, reason: String) {
        if (unplayable.size > MAX_REMEMBERED) unplayable.clear()
        unplayable[videoId] = Verdict(reason, MonotonicClock.nowMs())
    }

    /**
     * Forget every verdict recorded above.
     *
     * Signing in is the one event that can turn an age-gated track playable,
     * and signing out the one that can turn it back — so both have to clear
     * this, or the listener who signs in specifically to play a track is told
     * for the next ten minutes that it still cannot be played. InnerTubeX's
     * exclusions go with it: a client refused while anonymous is owed a fresh
     * hearing now that there is a session to send.
     */
    fun onSessionChanged() {
        InnerTubeXResolver.onSessionChanged()
        unplayable.clear()
        UploadedTracks.clear()
    }

    private const val UNPLAYABLE_TTL_MS = 10 * 60 * 1000L

    /**
     * One walk per videoId at a time.
     *
     * [AudioCache]'s read-ahead resolves the queued track before it is
     * reached, to warm the cache; if the queue advances faster than that
     * walk finishes, playback calls [resolve] for the same track a second
     * time before the first walk has populated [recent]. Left alone, that is
     * two full client walks in flight for the same track at once, each
     * paying for the other's requests — round trips measured elsewhere in
     * this file at ~250ms stretched past 3s under exactly this contention.
     * A second caller for a videoId already being resolved waits on the
     * first walk instead of starting its own.
     *
     * Parented to [resolverScope] rather than the caller's own coroutine, so
     * that a caller giving up on its own timeout — see
     * [PlaybackService][moe.rgsekai.sekaitune.shared.playback.PlaybackService] —
     * cancels only its own wait, not the walk a second caller may still be
     * waiting on. Being parented elsewhere is also why the walk has to be told
     * whose it is — [TrackLog.about] — rather than inheriting it: this is the
     * single largest producer of lines in the log, and every one of them was
     * previously filed against whatever happened to be playing while the walk
     * ran, which for read-ahead is the track before this one.
     */    private suspend fun coalescedResolve(videoId: String): Stream {
        synchronized(inFlightLock) { inFlight[videoId] }?.let { return it.await() }
        // Started lazily so that losing the race below costs nothing: the walk
        // that gets discarded has not run a line, so cancelling it fires no
        // requests and leaves no half-finished deferred behind.
        val walk = resolverScope.async(TrackLog.about(videoId), start = CoroutineStart.LAZY) {
            resolveUncached(videoId)
        }
        val running = synchronized(inFlightLock) { inFlight[videoId] ?: run { inFlight[videoId] = walk; null } }
        if (running != null) {
            walk.cancel()
            return running.await()
        }
        // Unregistered by the walk's own completion rather than by the awaiter,
        // which is the difference between coalescing and only appearing to.
        //
        // This used to be `try { deferred.await() } finally { inFlight.remove }`,
        // and the finally is the bug: the walk is parented to [resolverScope]
        // precisely so a caller giving up does not kill it, so a caller that
        // gives up — which is every read-ahead resolve the queue moves past, and
        // every [PlaybackService] resolve that hits its own timeout — took the
        // still-running walk out of the map on its way out. The next caller then
        // found nothing in flight and started a second full walk against the
        // same videoId, which is exactly what the logs show: two overlapping
        // resolves for one track, 4472ms and 4620ms, 2.8s of them concurrent,
        // each paying for the other's round trips. The doc comment above
        // promised one walk per videoId and the finally guaranteed the opposite.
        walk.invokeOnCompletion { synchronized(inFlightLock) { if (inFlight[videoId] === walk) inFlight.remove(videoId) } }
        walk.start()
        return walk.await()
    }

    private val inFlightLock = SynchronizedObject()

    private val inFlight = HashMap<String, Deferred<Stream>>()

    private val resolverScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private suspend fun resolveUncached(videoId: String): Stream {
        val resolveStart = MonotonicClock.nowMs()
        val stream = try {
            timed("$videoId InnerTubeX") {
                innerTubeXStreamAllowingUploads(videoId, requireM4a = StreamPlatform.playbackRequiresM4a)
            }
                ?: run {
                    TrackLog.w(TAG, "InnerTubeX found no usable stream for $videoId; falling back to extraction")
                    timed("$videoId newPipeStream") { newPipeStream(videoId, ::pickForQuality) }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (StreamPlatform.isLinkageError(e)) {
                // Every strategy above either runs third-party extraction code or
                // drives YouTube's player JavaScript, so any of them can turn out to
                // have been compiled against an API this OS version does not carry.
                // That arrives as an Error, which the clause below does not catch and
                // no caller of this function catches either — ExoPlayer's loader
                // thread least of all, which is where it surfaced as a process kill
                // rather than a failed track. Converted here, at the one point every
                // strategy passes through, so the answer is the same wherever the
                // linkage failure came from.
                TrackLog.w(
                    TAG,
                    "resolve hit a linkage failure for $videoId after " +
                        "${MonotonicClock.nowMs() - resolveStart}ms: ${e::class.qualifiedName}: ${e.message}",
                    e,
                )
                throw IOException("Stream resolution cannot run on this device: $e", e)
            }
            if (e !is Exception) throw e
            // The one path out of here that said nothing at all. A resolve that
            // throws is handed to ExoPlayer as a load error, which retries it on
            // a backoff of its own — so the symptom is a track that sits in
            // BUFFERING and walks the clients again every thirty seconds, with
            // no line anywhere naming what actually went wrong. The stack trace
            // is the point: the failure is usually several frames inside
            // NewPipe, where the message alone ("null", commonly) identifies
            // nothing.
            TrackLog.w(
                TAG,
                "resolve failed for $videoId after ${MonotonicClock.nowMs() - resolveStart}ms: " +
                    "${e::class.qualifiedName}: ${e.message}",
                e,
            )
            // Recorded before it is rethrown, so the retries stacked above this
            // — ExoPlayer's, the service's, read-ahead's — are answered from
            // memory instead of each one asking InnerTubeX and extracting three
            // times against a refusal that is never going to soften.
            permanentReason(e)?.let { reason ->
                rememberUnplayable(videoId, reason)
                TrackLog.w(TAG, "$videoId is not playable: $reason; not asking again for 10 minutes")
                throw PermanentlyUnplayableException(reason)
            }
            throw e
        }
        TrackLog.d(TAG, "TIMING $videoId total resolve: ${MonotonicClock.nowMs() - resolveStart}ms")
        return stream
    }

    /**
     * A probed stream from [InnerTubeXResolver], or null to fall through to extraction.
     *
     * A client whose URL fails the probe is skipped and InnerTubeX asked again, up
     * to [INNERTUBEX_ATTEMPTS] times, since its catalog has further clients behind it.
     */
    private suspend fun innerTubeXStream(
        videoId: String,
        maxKbps: Int = maxKbps(),
        requireM4a: Boolean = false,
        uploaded: Boolean = false,
        escalateToUpload: Boolean = true,
        playRefusedUpload: Boolean = false,
    ): Stream? {
        val skip = mutableSetOf<String>()
        repeat(INNERTUBEX_ATTEMPTS) { attempt ->
            val found = try {
                InnerTubeXResolver.extract(videoId, maxKbps, skip, requireM4a, uploaded)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TrackLog.w(TAG, "InnerTubeX failed for $videoId: ${e::class.simpleName}: ${e.message}")
                return null
            } ?: return null
            val verdict = timed("$videoId InnerTubeX ${found.profileId} probe") { probe(found.url) }
            if (verdict == Probe.OK) {
                TrackLog.d(TAG, "resolved $videoId via InnerTubeX ${found.profileId} @ ${found.kbps}kbps")
                return Stream(found.url, found.kbps, found.mimeType, found.loudnessDb)
            }
            TrackLog.w(TAG, "InnerTubeX ${found.profileId} minted an unusable URL for $videoId: $verdict")

            if (playRefusedUpload && uploaded && verdict == Probe.REFUSED) {
                TrackLog.w(TAG, "playing $videoId's upload stream despite the probe: it is the only copy")
                return Stream(found.url, found.kbps, found.mimeType, found.loudnessDb)
            }
            skip += found.profileId

            if (escalateToUpload && attempt == 0 && !uploaded &&
                verdict == Probe.REFUSED && !Innertube.cookie.isNullOrBlank()
            ) {
                askAsUpload(videoId, maxKbps, requireM4a)?.let { return it }
            }
        }
        return null
    }

    /** One ask for [videoId] as the user's own upload; a hit is remembered in [UploadedTracks]. */
    private suspend fun askAsUpload(videoId: String, maxKbps: Int, requireM4a: Boolean): Stream? {
        TrackLog.d(TAG, "first stream for $videoId was refused; asking as an upload")
        val found = try {
            InnerTubeXResolver.extract(videoId, maxKbps, emptySet(), requireM4a, uploaded = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TrackLog.w(TAG, "asking for $videoId as an upload failed: ${e::class.simpleName}: ${e.message}")
            return null
        }
        if (found == null) {
            TrackLog.w(TAG, "no stream for $videoId when asked as an upload")
            return null
        }
        val verdict = timed("$videoId InnerTubeX ${found.profileId} upload probe") { probe(found.url) }
        if (verdict != Probe.OK) {
            TrackLog.w(TAG, "InnerTubeX ${found.profileId} as an upload for $videoId: $verdict")
            return null
        }
        TrackLog.d(TAG, "resolved $videoId as an upload via InnerTubeX ${found.profileId} @ ${found.kbps}kbps")
        UploadedTracks.mark(videoId)
        return Stream(found.url, found.kbps, found.mimeType, found.loudnessDb)
    }

    /**
     * [innerTubeXStream], asked as an upload from the start when the track is
     * known to be one. An unknown one is escalated by [innerTubeXStream]
     * itself when its first stream is refused while signed in — asking again
     * here would put up to [INNERTUBEX_ATTEMPTS] more extractions in front of
     * every signed-in failure before the extraction fallback gets a turn.
     */
    private suspend fun innerTubeXStreamAllowingUploads(
        videoId: String,
        maxKbps: Int = maxKbps(),
        requireM4a: Boolean = false,
    ): Stream? {
        val known = UploadedTracks.contains(videoId)
        if (known) {
            innerTubeXStream(videoId, maxKbps, requireM4a, uploaded = true, playRefusedUpload = true)
                ?.let { return it }
            TrackLog.w(TAG, "$videoId is marked as an upload but did not resolve as one; trying an ordinary ask")
            return innerTubeXStream(videoId, maxKbps, requireM4a, uploaded = false, escalateToUpload = false)
        }
        return innerTubeXStream(videoId, maxKbps, requireM4a)
    }

    /** The headers a media fetch for [url] must carry to match whoever minted it. */
    fun mediaHeadersFor(url: String): Map<String, String> =
        InnerTubeXResolver.headersFor(url) ?: PlayerClient.forStreamUrl(url).mediaHeaders()

    private const val INNERTUBEX_ATTEMPTS = 3

    /** Logs how long [block] took, whatever it returns — a timing probe, not a control flow change. */
    private suspend inline fun <T> timed(label: String, block: suspend () -> T): T {
        val start = MonotonicClock.nowMs()
        return block().also { TrackLog.d(TAG, "TIMING $label: ${MonotonicClock.nowMs() - start}ms") }
    }

    /**
     * A resolved stream: the URL, and what the format behind it turned out to
     * be. Playback only ever needs the URL; a download needs the rest of it to
     * name the file and declare its type.
     */
    class Stream(val url: String, val kbps: Int, val mimeType: String, val loudnessDb: Double? = null) {

        /**
         * The container these bytes are actually in, which is not always what
         * names them.
         *
         * Nothing here transcodes or remuxes — what googlevideo sends is what
         * lands on disk — so the extension has to describe the bytes rather
         * than the codec inside them. YouTube's Opus is Opus-in-WebM, and the
         * two sources disagree about how to say so: the player endpoint calls
         * it `audio/webm; codecs="opus"` and NewPipe calls it `audio/opus`.
         * Taking the latter at face value would write a WebM file named
         * `.opus`, and an `.opus` file is expected to be Ogg — which is how a
         * perfectly good download ends up refusing to open in half the players
         * on the device.
         *
         * App-private downloads can keep the WebM rendition, while exported
         * downloads require MP4. Playback also hands Opus around, and a file an
         * older build already wrote is still a `.webm` this app has to be able
         * to describe.
         */
        val downloadExtension: String
            get() = when {
                "mp4" in mimeType || "m4a" in mimeType -> "m4a"
                else -> "webm"
            }

        /** What the media store should be told this file is. */
        val downloadMimeType: String
            get() = if (downloadExtension == "m4a") "audio/mp4" else "audio/webm"
    }

    /**
     * As [resolve], but for a file being kept rather than a stream being heard:
     * the best format the *download* setting allows, and no connection has a
     * say. Opus-in-WebM is preferred for app-private downloads; exported files
     * require AAC-in-MP4 because Android's Music collection rejects WebM.
     *
     * Every adaptive audio format YouTube offers is either AAC in MP4 or Opus
     * (or Vorbis) in WebM. Android's media store will not mint an audio row for
     * `audio/webm` — measured on-device as
     * `IllegalArgumentException: Unsupported MIME type audio/webm` out of
     * `ContentResolver.insert`, thrown before a single byte has been fetched.
     * App-private storage has no such restriction, so it keeps Opus; an export
     * asks for MP4 from the start instead of discovering the restriction when
     * it creates the destination.
     *
     * There is no "best available" fallback below. A walk that takes another
     * container can no longer fulfil the destination already chosen: WebM
     * cannot be exported through the Music collection, and silently replacing
     * a requested private Opus download with AAC changes its codec. Running out
     * of the required ladder is therefore a failure with a sentence attached.
     *
     * The container is asked of InnerTubeX up front rather than filtered out
     * of whatever it picks, because it answers with one format per ask: MP4
     * when the destination needs it, its best Opus otherwise. It has no
     * bitrate ceiling of its own, so a rung over [maxKbps] sends the download
     * on to extraction for a lower one, and is kept should extraction fail.
     *
     * Nothing here touches [recent]. That cache exists to keep ExoPlayer's
     * re-opens off the network, and its entries are picked under the *playback*
     * ceiling — seeding it from here would hand a capped connection a stream it
     * was capped to avoid, and reading from it would hand a download whatever
     * bitrate playback happened to settle for. Both directions are wrong, and
     * they are wrong independently of what [maxKbps] says.
     *
     * Extraction sits behind InnerTubeX for the same reason it does in
     * [resolve]: a download reaching a wall playback climbs over has to climb
     * it too, or it fails while the track it is refusing to save is audibly
     * playing.
     *
     * @param maxKbps the ceiling from
     *   [DownloadQuality][moe.rgsekai.sekaitune.shared.data.settings.DownloadQuality].
     *   Passed in rather than read here so that one download resolves at one
     *   bitrate: a setting changed mid-fetch, or a re-resolve after a refusal
     *   (see [Downloader.fetch][Downloader.fetch]),
     *   must not splice two different renditions into one file.
     */
    suspend fun resolveForDownload(
        videoId: String,
        maxKbps: Int,
        requireM4a: Boolean = false,
    ): Stream {
        val stream = downloadStream(videoId, maxKbps, requireM4a)
        val allowed = if (requireM4a) setOf("m4a") else setOf("m4a", "webm")
        check(stream.downloadExtension in allowed) {
            "Can't save ${stream.mimeType} — try again"
        }
        return stream
    }

    private suspend fun downloadStream(videoId: String, maxKbps: Int, requireM4a: Boolean): Stream =
        withContext(TrackLog.about(videoId)) {
            StreamPlatform.init()

            // Whether any client offered AAC at all, as distinct from whether one
            // could be turned into a working URL. Those are different failures and
            // only one of them is worth telling someone to try again about: a track
            // no client has an MP4 for will not have one in five minutes either,
            // while an MP4 that won't probe is a bad afternoon on Google's side.
            var offered = false

            // InnerTubeX hands back its best rung of the container asked for, and
            // has no ceiling of its own. Within the setting's ceiling it is the
            // answer; above it (best Opus over Standard's 128kbps) extraction
            // below can pick the lower rung, and this is kept as the fallback.
            val found = innerTubeXStreamAllowingUploads(videoId, maxKbps, requireM4a)
                ?.takeIf { if (requireM4a) it.downloadExtension == "m4a" else it.downloadExtension == "webm" }
            if (found != null) {
                offered = true
                if (found.kbps <= maxKbps) return@withContext found
                TrackLog.d(TAG, "InnerTubeX's ${found.kbps}kbps is over the ${maxKbps}kbps download ceiling for $videoId")
            }

            // The failsafe changes how the URL is found, not which container the
            // destination can accept.
            val format = if (requireM4a) "MP4" else "Opus"
            if (found == null) TrackLog.w(TAG, "InnerTubeX found no usable $format for $videoId; extracting")
            runCatching {
                newPipeStream(videoId) { candidates ->
                    // Capped the same way as the player-response selection, off
                    // the same setting, or the failsafe would quietly hand back
                    // a rendition the user said they didn't want to keep.
                    val matching = candidates.filter {
                        if (requireM4a) it.second.isM4a else it.second.isWebmOpus
                    }
                    underCeiling(matching, maxKbps)
                        ?.also { offered = true }
                }
            }.onSuccess { return@withContext it }
                .onFailure { TrackLog.w(TAG, "extraction found no $format for $videoId: ${it.message}") }

            // A rung above the ceiling beats no download at all; extraction does the same.
            found?.let { return@withContext it }
            if (offered) error("Couldn't reach a downloadable copy just now — try again")
            error("No downloadable audio for this track")
        }

    // ---- Format selection ---------------------------------------------------

    /**
     * Highest stream at or under the ceiling set for the connection in use; if
     * everything is above it (e.g. Low on a track that only has 130kbps+), take
     * the cheapest available rather than failing.
     */
    private fun <T> pickForQuality(candidates: List<Pair<Int, T>>): T? =
        underCeiling(candidates, maxKbps())

    /**
     * Highest of [candidates] at or under [maxKbps]; if everything is above it
     * — Standard on a track whose AAC ladder starts at 256, say — the cheapest
     * available, because a rung over budget still beats no audio at all.
     */
    private fun <T> underCeiling(candidates: List<Pair<Int, T>>, maxKbps: Int): T? {
        if (candidates.isEmpty()) return null
        val withinBudget = candidates.filter { it.first <= maxKbps }
        return (withinBudget.maxByOrNull { it.first } ?: candidates.minByOrNull { it.first })
            ?.second
    }

    /**
     * YouTube's current player revision, as the number a client quotes to prove
     * it is running that player.
     *
     * Named and shared rather than fetched at each of the three call sites,
     * because there is now a fourth kind of caller that is nothing to do with
     * streaming: [PlaybackTracker] needs one to be issued a tracking block at
     * all, and it has no business knowing that the value comes from parsing
     * YouTube's player JavaScript.
     *
     * Memoised for the process. It is a property of YouTube's deployment, not of
     * a track — the videoId is passed only because NewPipe's API takes one, to
     * decide which player script to fetch — and it changes on the order of days,
     * against a first parse that costs seconds.
     */
    suspend fun signatureTimestamp(videoId: String): Int? {
        cachedSignatureTimestamp?.let { return it }
        return runCatching {
            StreamPlatform.signatureTimestamp(videoId)
        }
            .onFailure { TrackLog.w(TAG, "no signature timestamp: ${it.message}") }
            .getOrNull()
            ?.also { cachedSignatureTimestamp = it }
    }

    @Volatile
    private var cachedSignatureTimestamp: Int? = null

    // ---- Validation ---------------------------------------------------------

    private enum class Probe {
        /** Served media bytes; safe to play and to cache. */
        OK,

        /** Answered, but refused this request — the client is the problem. */
        REFUSED,

        /** Never got an answer worth interpreting; blame nothing in particular. */
        UNREACHABLE,
    }

    /**
     * Read the end of a URL before trusting it.
     *
     * This is the whole difference between a track that fails and a track that
     * fails *visibly and instantly*. A URL that 403s is indistinguishable from
     * a good one until something reads from it; hand it to ExoPlayer and the
     * failure surfaces as a track that spins and never starts.
     *
     * The range has to be as large as the real fetch will ask for, not a token
     * one. A URL minted for a session Google has reservations about serves
     * small ranges to anybody — enough to pass a small probe — and then refuses
     * the multi-megabyte ranges actual listening is made of with a 403.
     * The range starts past [AUTH_BOUNDARY_BYTES] when the file is that long:
     * some clients' URLs serve the first megabyte and 403 everything after
     * it, so a probe of the opening passes and playback dies ~50s in.
     * Sixteen kilobytes of the answer still have to actually arrive, so a
     * response that stalls after its headers is a failure too.
     *
     * The headers are the ones the media fetch will really use — see
     * [PlayerClient.forStreamUrl] — so this tests the request that matters
     * rather than a more favourable version of it.
     */
    private fun probe(url: String): Probe {
        val length = queryParam(url, "clen")?.toLongOrNull()
        val start = if (length != null && length > AUTH_BOUNDARY_BYTES + PROBE_READ_BYTES) AUTH_BOUNDARY_BYTES else 0L
        // A host with no range cap of its own reports Long.MAX_VALUE, which
        // must not be added to [start]: that overflowed into a negative end
        // and a 416 read as a refusal. Its real fetch asks for the rest of
        // the file, so the probe does too.
        val window = PlayerClient.rangeBytesFor(url)
        val range = when {
            window != Long.MAX_VALUE -> "bytes=$start-${minOf(start + window, length ?: Long.MAX_VALUE) - 1}"
            length != null -> "bytes=$start-${length - 1}"
            else -> "bytes=$start-"
        }
        val sentHeaders = mediaHeadersFor(url)
        // Same order as before: Range first, the media headers override it by name.
        return try {
            StreamPlatform.probeFetch(url, mapOf("Range" to range) + sentHeaders, PROBE_READ_BYTES) { code, contentType, hasBody ->
                val verdict = when {
                    code in REFUSAL_CODES -> Probe.REFUSED
                    code !in 200..299 && code != 416 -> Probe.UNREACHABLE
                    // A refusal dressed as a success: an error page, or the
                    // consent/captcha interstitial, rather than audio.
                    contentType?.startsWith("audio/") != true -> Probe.REFUSED
                    // Headers can arrive long before a body that never does —
                    // exactly the shaping this whole path exists to sidestep.
                    // Insisting on the bytes is the point: a trickle that
                    // yields its first byte and stalls is a failure too.
                    !hasBody() -> Probe.UNREACHABLE
                    else -> Probe.OK
                }
                if (verdict != Probe.OK) {
                    TrackLog.w(
                        TAG,
                        "probe $verdict: HTTP $code type=$contentType " +
                            "range=$range ${describeMediaUrl(url)} " +
                            "sent=${sentHeaders.keys.sorted()}",
                    )
                }
                verdict
            }
        } catch (e: Exception) {
            TrackLog.w(TAG, "probe failed: ${e.message}")
            Probe.UNREACHABLE
        }
    }

    private val REFUSAL_CODES = setOf(403, 404, 410)

    /** A stream URL reduced to what describes it and nothing that could be replayed. */
    private fun describeMediaUrl(url: String): String {
        val parsed = parseUrl(url) ?: return "url=unparseable"
        return "host=${parsed.host} path=${parsed.encodedPath} " +
            "c=${parsed.parameters["c"]} itag=${parsed.parameters["itag"]} " +
            "mime=${parsed.parameters["mime"]} clen=${parsed.parameters["clen"]} " +
            "pot=${parsed.parameters["pot"] != null}"
    }

    private fun parseUrl(url: String): Url? = runCatching { Url(url) }.getOrNull()
        ?.takeIf { it.protocol.name == "http" || it.protocol.name == "https" }

    private fun queryParam(url: String, name: String): String? = parseUrl(url)?.parameters?.get(name)

    /** Where googlevideo stops authorising some clients' URLs (InnerTubeX: "CDN 403 after 1 MiB"). */
    private const val AUTH_BOUNDARY_BYTES = 1024L * 1024

    /** How much of the answer must actually arrive, to catch a stalled body. */
    private const val PROBE_READ_BYTES = 16L * 1024

    /**
     * A URL that [probe] cleared has been refused while actually playing.
     *
     * [probe] runs before playback and not again, so without this [recent]
     * would keep handing back the same dead URL for the rest of its TTL and
     * every replay would fail the same way. So the refusal is fed back: forget
     * the URL, and when InnerTubeX minted it, skip that client for the track.
     *
     * Called from the playback path — see
     * [ChunkedDataSource][moe.rgsekai.sekaitune.shared.playback.ChunkedDataSource].
     */
    fun onPlaybackRefused(url: String, responseCode: Int) {
        if (responseCode !in REFUSAL_CODES) return
        // Only googlevideo's URLs say anything about the client that minted
        // them. A module's stream URL answering 404 is that server's business,
        // and must not bench a YouTube client.
        if (parseUrl(url)?.host?.endsWith("googlevideo.com") != true) return
        val videoId = InnerTubeXResolver.onRefused(url)
            // The map is a latency cache of a few dozen entries, so finding the
            // way back from a URL costs nothing worth measuring.
            ?: recent.entries.firstOrNull { it.value.url == url }?.key
            ?: return
        recent.remove(videoId)
    }

    // ---- Failsafe -----------------------------------------------------------

    /**
     * NewPipe's full extractor, kept for the case where every player client has
     * been turned away — it re-derives everything itself and is updated
     * upstream when YouTube changes, so it works when nothing else does.
     *
     * Last rather than first because of what it costs: it scrapes the watch
     * page, which is the request Google shapes hardest, and a shaped response
     * can hold this call open for the better part of a minute. Worth waiting
     * out when the alternative is silence; not worth paying for every track.
     *
     * Driven through [StreamExtractor][org.schabi.newpipe.extractor.stream.StreamExtractor]
     * directly rather than through `StreamInfo.getInfo`, which is the obvious
     * call and the expensive one. `getInfo` assembles everything a video page
     * has — it drives forty-odd extractor methods, among them `getVideoStreams`
     * and `getVideoOnlyStreams`, and YouTube answers those with twenty to
     * thirty formats whose `n` parameter each has to be transformed by running
     * YouTube's player JavaScript. This app then discards every one of them and
     * keeps the audio. Fetching the page and asking only for [audioStreams]
     * pays that transform four or five times instead: measured on-device at
     * 49.8s against 2.3s for the same track over the same connection.
     */
    private suspend fun newPipeStream(
        videoId: String,
        select: (List<Pair<Int, NewPipeAudio>>) -> NewPipeAudio?,
    ): Stream {
        // ponytail: no NewPipe on iOS; InnerTubeX is the only resolver there
        if (!StreamPlatform.newPipeAvailable) error("Track unavailable: no audio streams")
        var failure: Exception? = null
        repeat(EXTRACTION_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(EXTRACTION_RETRY_MS * attempt)
            try {
                return StreamPlatform.extractStream(videoId, select)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (StreamPlatform.isLinkageError(e)) {
                    // Not a failed extraction — a method or class the extractor was
                    // compiled against that this OS version does not have, which is
                    // the same answer every time and so is not worth the remaining
                    // attempts. It reaches here as an Error rather than an Exception,
                    // and an Error let past this point does not fail the track: it
                    // unwinds through ExoPlayer's loader thread, where nothing is
                    // catching it, and takes the process with it. That is what a
                    // NoSuchMethodError out of NewPipe's URL codec did on every
                    // Android below 13 — see the note in the vendored
                    // `org.schabi.newpipe.extractor.utils.Utils`. Reported as an
                    // Exception so the layers above treat it as the load failure it
                    // is, with the type name kept because the message alone ("No
                    // static method ...") reads like nothing.
                    throw IOException("Extractor cannot run on this device: $e", e)
                }
                if (e !is Exception) throw e
                // A verdict about the content, not about this minute. There was
                // no classification here at all, so the most expensive retry in
                // the app was spent on the one class of failure that cannot
                // benefit from it: an age-restricted track threw
                // AgeRestrictedContentException, which extends
                // ContentNotAvailableException extends ParsingException extends
                // Exception, landed in this clause, and was granted all three
                // attempts with 1s and 2s backoffs — each one re-fetching the
                // watch page and holding [extractionGate] against every other
                // resolve in the process while it did. Three identical refusals
                // per walk, and the walk itself repeated. Named and rethrown on
                // the first one instead.
                permanentReason(e)?.let { reason ->
                    TrackLog.w(TAG, "extraction will not succeed for $videoId: $reason")
                    throw e
                }
                // Worth another go rather than worth giving up on: the common
                // failure here is a shaped or cut-off watch page, which is a
                // fact about this minute rather than about the track, and this
                // is the last thing standing between the listener and silence.
                TrackLog.w(
                    TAG,
                    "extraction attempt ${attempt + 1} of $EXTRACTION_ATTEMPTS failed for $videoId: ${e.message}",
                )
                failure = e
            }
        }
        throw failure ?: IllegalStateException("Track unavailable: no audio streams")
    }

    /**
     * Why [e] means "never", or null if it only means "not just now".
     *
     * The distinction is the difference between one failed track and a request
     * storm, and it is not available from the exception hierarchy: NewPipe files
     * a takedown, a region block and a truncated watch page under the same
     * [ParsingException] ancestry, so a `catch (e: Exception)` treats "this
     * video does not exist" exactly like "the page arrived cut in half". Every
     * retry in this app sat downstream of that conflation.
     *
     * Kept as a message rather than a boolean because the message is what the
     * listener eventually sees, and "This video is age-restricted" is a
     * different thing to be told than `ERROR_CODE_IO_UNSPECIFIED`.
     */
    private fun permanentReason(e: Throwable): String? = when (e) {
        is PermanentlyUnplayableException -> e.message ?: "This track cannot be played"
        // ExoPlayer and the coroutine machinery both wrap freely, and the
        // classification has to survive being wrapped or it never fires: the
        // failure that reaches [resolveUncached] arrives as whatever the last
        // layer chose to throw it as.
        else -> StreamPlatform.permanentReason(e) ?: e.cause?.takeIf { it !== e }?.let(::permanentReason)
    }

    /** How many times the watch page is worth asking for before giving up. */
    private const val EXTRACTION_ATTEMPTS = 3

    /** Multiplied by the attempt number, so the wait grows if the first retry doesn't take. */
    private const val EXTRACTION_RETRY_MS = 1000L

    // ---- Cache --------------------------------------------------------------

    /**
     * How many bytes the whole track is, or null if the URL doesn't say.
     *
     * Every progressive googlevideo URL carries the figure as `clen`. It is
     * worth reading from there because the alternative is an HTTP request that
     * reaches the end of the resource: a bounded range never reveals the total,
     * so read-ahead would have no way to know when it was finished. Resolving
     * is memoised, so asking costs nothing beyond the first time.
     */
    suspend fun contentLength(videoId: String): Long? =
        queryParam(resolve(videoId), "clen")?.toLongOrNull()

    private class Resolved(val url: String, val at: Long)

    /**
     * Stream URLs already resolved, by videoId — and, since [resolve] only ever
     * stores one that has served bytes, already known good rather than merely
     * recent.
     *
     * Google issues them with several hours of validity, so the ceiling here is
     * chosen for a different reason: a URL is tied to the playback session that
     * minted it, and holding one indefinitely means a stale entry survives long
     * enough to fail a play. Twenty minutes covers a track and the seeking
     * around it while staying well inside the window where the URL is good.
     */
    private val recent = ConcurrentMap<String, Resolved>()

    private const val URL_TTL_MS = 20 * 60 * 1000L

    /** Enough for the queue in hand; this is a latency cache, not a store. */
    private const val MAX_REMEMBERED = 32

    private fun remember(videoId: String, url: String) {
        if (recent.size >= MAX_REMEMBERED) {
            val cutoff = MonotonicClock.nowMs() - URL_TTL_MS
            recent.entries.removeAll { it.value.at < cutoff }
            if (recent.size >= MAX_REMEMBERED) recent.clear()
        }
        recent[videoId] = Resolved(url, MonotonicClock.nowMs())
    }
}
