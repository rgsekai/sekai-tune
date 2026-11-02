package moe.rgsekai.sekaitune.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToSvgPainter
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/** A signature is drawn at this share of a title logo's size. */
private const val SIGNATURE_SCALE = 0.7f

/** The widest share of the page a title logo takes, and the tallest share of the photo. */
private const val TITLE_WIDTH = 0.82f
private const val TITLE_MAX_HEIGHT = 0.30f

/** A dropped image fetch gets two more tries, 800 ms apart, before the name is left in place. */
private const val IMAGE_RETRIES = 2
private const val IMAGE_RETRY_DELAY_MS = 800L

/**
 * Android decodes vectors at this width so a small file stays crisp laid out large. A vector
 * painter has no decode size, so here it only stands in for the memory the same image would hold.
 */
private const val SVG_DECODE_WIDTH = 1600

/**
 * Lookup retries: a sleeping self-hosted API needs ~30–60 s to wake (Render's free tier), and a
 * single miss would otherwise stand until the page is reopened. Genuine misses are not cached, so
 * retrying is cheap. Covers roughly a full wake cycle.
 */
private val LOOKUP_BACKOFF_MS = listOf(0L, 5_000L, 15_000L, 30_000L)

/**
 * The artist's name on the banner — in their own hand when a signature API is set up, with a
 * "Signed by" line under it. Android's `ArtistHeader`, less Apple's title logo, which desktop does
 * not have: here a signature is always the one thing that can stand in for the name.
 *
 * The name stays until the image has actually been drawn, so the banner is never left with nothing
 * in it.
 */
@Composable
internal fun BoxScope.DesktopArtistTitle(
    name: String,
    signature: DesktopArtistSignature?,
    bannerHeight: Dp,
    gutter: Dp,
) {
    var shown = false
    if (signature != null) {
        val url = signature.imageUrl
        var attempt by remember(url) { mutableIntStateOf(0) }
        var failed by remember(url) { mutableStateOf(false) }
        val density = LocalDensity.current
        val painter by produceState<Painter?>(null, url, attempt, density) {
            val loaded = withContext(Dispatchers.IO) { DesktopSignatureImages.load(signature, density) }
            value = loaded
            if (loaded == null) failed = true
        }
        LaunchedEffect(failed) {
            if (failed && attempt < IMAGE_RETRIES) {
                delay(IMAGE_RETRY_DELAY_MS)
                failed = false
                attempt++
            }
        }
        val loaded = painter
        if (loaded != null) {
            shown = true
            val aspect = signature.aspect.coerceIn(0.4f, 6f)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = gutter, end = gutter),
            ) {
                Image(
                    painter = loaded,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    // Vectors are dark ink on transparency, which vanishes over a dark photo, so
                    // they are tinted white; scans were turned into white ink as they loaded.
                    colorFilter = if (signature.format == "svg") ColorFilter.tint(Color.White) else null,
                    modifier = Modifier
                        .padding(top = 14.dp, bottom = 6.dp)
                        // Sized from the incoming width in layout, and capped in height too: by
                        // width alone a near-square one would swallow the photograph it sits on.
                        .layout { measurable, constraints ->
                            val widest = constraints.maxWidth * TITLE_WIDTH * SIGNATURE_SCALE
                            val tallest = bannerHeight.toPx() * TITLE_MAX_HEIGHT * SIGNATURE_SCALE
                            val height = minOf(widest / aspect, tallest).roundToInt()
                            val width = (height * aspect).roundToInt()
                            val placeable = measurable.measure(Constraints.fixed(width, height))
                            layout(width, height) { placeable.place(0, 0) }
                        },
                )
                SignedByCaption(name, Modifier.padding(bottom = 18.dp))
            }
        }
    }
    if (!shown) {
        Text(
            name,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = gutter, end = gutter, bottom = 18.dp),
        )
    }
}

@Composable
private fun SignedByCaption(name: String, modifier: Modifier = Modifier) {
    val signed = DesktopStrings.format("signed_by", name, fallback = "Signed by %1\$s")
    val nameAt = signed.indexOf(name)
    Text(
        text = buildAnnotatedString {
            append(signed)
            if (nameAt >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), nameAt, nameAt + name.length)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.8f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * The signature to draw for [name] now, or null — Android's header effect.
 *
 * Re-runs when the toggle, the address or [DesktopArtistSignatures.updates] change. The last
 * signature found is kept through a miss, and a miss is retried on [LOOKUP_BACKOFF_MS].
 */
@Composable
internal fun rememberDesktopArtistSignature(name: String): DesktopArtistSignature? {
    val enabled by DesktopSignatureSettings.enabled.collectAsState()
    val url by DesktopSignatureSettings.url.collectAsState()
    val updates by DesktopArtistSignatures.updates.collectAsState()
    val base = DesktopArtistSignatures.effectiveBaseUrl(enabled, url)
    var signature by remember(name) { mutableStateOf<DesktopArtistSignature?>(null) }
    LaunchedEffect(name, enabled, url, updates) {
        if (base == null) {
            signature = null
            return@LaunchedEffect
        }
        for (wait in LOOKUP_BACKOFF_MS) {
            if (wait > 0) delay(wait)
            signature = DesktopArtistSignatures.cached(name, base)
                ?: DesktopArtistSignatures.signatureFor(name, base)
                ?: signature
            if (signature != null) return@LaunchedEffect
        }
    }
    return signature
}

/**
 * Signature images, kept the way Android's Coil keeps them: decoded in memory up to a fifth of the
 * heap, and the downloaded file on disk up to 100 MB, least recently used going first.
 */
internal object DesktopSignatureImages {

    private class Loaded(val painter: Painter, val bytes: Long)

    private val memory = DesktopByteBudgetLru<Loaded>((Runtime.getRuntime().maxMemory() * 0.20).toLong()) { it.bytes }

    private const val DISK_LIMIT_BYTES = 100L * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 16 * 1024 * 1024

    /** Android's Coil client names itself, since Wikimedia (which serves signature art) 403s generic ones. */
    private const val USER_AGENT = "SekaiTune/desktop (+https://github.com/rgsekai/sekai-tune-desktop)"

    private val directory: Path by lazy {
        DesktopMediaCache.directory.resolveSibling("images")
            .also { runCatching { Files.createDirectories(it) } }
    }

    /** The drawable for [signature], from memory, then disk, then the network. Null when it can't be had. */
    fun load(signature: DesktopArtistSignature, density: Density): Painter? {
        val key = signature.imageUrl
        memory.get(key)?.let { return it.painter }
        val bytes = fromDisk(key) ?: fromNetwork(key) ?: return null
        val isSvg = signature.format == "svg"
        val painter = runCatching {
            if (isSvg) {
                bytes.decodeToSvgPainter(density)
            } else {
                // Scanned paper goes through the ink transformation; a photograph passes untouched.
                val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
                BitmapPainter(image.signatureInk().toComposeImageBitmap())
            }
        }.getOrNull() ?: return null
        val aspect = signature.aspect.coerceIn(0.4f, 6f)
        val size = if (isSvg) {
            SVG_DECODE_WIDTH.toLong() * (SVG_DECODE_WIDTH / aspect).roundToInt() * 4
        } else {
            (painter as BitmapPainter).intrinsicSize.let { it.width.toLong() * it.height.toLong() * 4 }
        }
        memory.put(key, Loaded(painter, size))
        return painter
    }

    /** Memory and disk both, dropped — what "Clear image cache" does. */
    fun clear() {
        memory.clear()
        files().forEach { runCatching { Files.deleteIfExists(it) } }
    }

    private fun fileFor(url: String): Path = directory.resolve(
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(32),
    )

    private fun fromDisk(url: String): ByteArray? = runCatching {
        val file = fileFor(url)
        if (!Files.isReadable(file)) return null
        // Touched so the oldest-read file is the one trimmed.
        file.toFile().setLastModified(System.currentTimeMillis())
        Files.readAllBytes(file).takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun fromNetwork(url: String): ByteArray? = runCatching {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        if (connection.responseCode !in 200..299) return null
        val bytes = connection.inputStream.use { it.readNBytes(MAX_IMAGE_BYTES) }.takeIf { it.isNotEmpty() } ?: return null
        runCatching { Files.write(fileFor(url), bytes) }.onSuccess { trim() }
        bytes
    }.getOrNull()

    private fun files(): List<Path> = runCatching {
        Files.list(directory).use { stream -> stream.filter(Files::isRegularFile).toList() }
    }.getOrDefault(emptyList())

    private fun trim() {
        var total = files().sumOf { runCatching { Files.size(it) }.getOrDefault(0L) }
        if (total <= DISK_LIMIT_BYTES) return
        files()
            .sortedBy { runCatching { Files.readAttributes(it, BasicFileAttributes::class.java).lastModifiedTime().toMillis() }.getOrDefault(0L) }
            .forEach {
                if (total <= DISK_LIMIT_BYTES) return
                val size = runCatching { Files.size(it) }.getOrDefault(0L)
                if (runCatching { Files.deleteIfExists(it) }.getOrDefault(false)) total -= size
            }
    }
}

/**
 * The signature API's address, on a card like the one an addon is added with. Android's
 * `SignatureApiEditor`.
 *
 * Saves only once `GET /v1/health` answers 2xx; removing forgets the provider. Either way what was
 * looked up against the old address is dropped.
 */
@Composable
internal fun DesktopSignatureApiDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val stored = DesktopSignatureSettings.url.value
    // A draft of the stored address, seeded once.
    var input by remember { mutableStateOf(stored) }
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val connected = DesktopStrings["connected", "Connected"]
    val failed = DesktopStrings["artist_signatures_test_failed", "Could not reach the API at that URL"]

    fun test(onGood: () -> Unit) {
        val normalized = DesktopArtistSignatures.normalizeBaseUrl(input)
        if (!DesktopArtistSignatures.isValidBaseUrl(normalized)) {
            status = failed to false
            return
        }
        busy = true
        status = null
        scope.launch {
            val ok = DesktopArtistSignatures.checkHealth(normalized)
            busy = false
            status = (if (ok) connected else failed) to ok
            if (ok) onGood()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(DesktopStrings["artist_signatures_url", "Signature API URL"]) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    DesktopStrings["artist_signatures_subtitle", "Shows a handwritten signature when Apple Music has no title logo"],
                    color = DesktopSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = input,
                    // A result describes the address it was run against, so editing clears it.
                    onValueChange = {
                        input = it
                        status = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(DesktopStrings["artist_signatures_url_hint", "https://signatures.example.com"]) },
                    singleLine = true,
                )
                status?.let { (text, good) ->
                    Text(
                        text,
                        color = if (good) DesktopAccent else DesktopSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        dismissButton = {
            Row {
                // The build's own default is not a saved address, so there is nothing to remove.
                if (stored.isNotBlank() && stored != DesktopSignatureSettings.defaultUrl) {
                    TextButton(onClick = {
                        DesktopSignatureSettings.setUrl("")
                        DesktopArtistSignatures.clear()
                        onDismiss()
                    }) { Text(DesktopStrings["remove", "Remove"]) }
                }
                TextButton(onClick = onDismiss) { Text(DesktopStrings["cancel", "Cancel"]) }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { test {} }, enabled = input.isNotBlank() && !busy) {
                    Text(DesktopStrings["test", "Test"])
                }
                TextButton(
                    onClick = {
                        test {
                            DesktopSignatureSettings.setUrl(input)
                            DesktopArtistSignatures.clear()
                            onDismiss()
                        }
                    },
                    enabled = input.isNotBlank() && !busy,
                ) { Text(DesktopStrings["save", "Save"]) }
            }
        },
    )
}
