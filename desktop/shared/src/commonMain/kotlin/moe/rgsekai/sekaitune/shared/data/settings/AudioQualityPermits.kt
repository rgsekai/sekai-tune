package moe.rgsekai.sekaitune.shared.data.settings

import moe.rgsekai.sekaitune.shared.data.sources.SourceKind

/**
 * Whether a stream started under this ceiling may be served by [kind].
 *
 * Asked per stream rather than written into
 * [SourceConfig.enabled][moe.rgsekai.sekaitune.shared.data.sources.SourceConfig.enabled],
 * which is what this used to do — an `applyQualityPreset` call flipped the
 * module and generic source switches the moment a rung was picked. Two things
 * were wrong with that and both were reported together: picking a rung for
 * *mobile data* turned the sources off while sitting on Wi-Fi, and nothing
 * turned them back on when the connection changed, so a Wi-Fi ceiling of
 * Lossless still had no lossless source to reach. A ceiling is a property
 * of the connection in force; the switches on the Sources screen are the
 * user's standing choice. Storing the first in the second lost the second.
 *
 * [SourceKind.YOUTUBE] is permitted on every rung: it is what [maxKbps]
 * caps, and it is the only source that can answer at all when the ones
 * above it are skipped.
 */
fun AudioQuality.permits(kind: SourceKind): Boolean = when (this) {
    AudioQuality.LOSSLESS -> true
    // No lossless answer is wanted here, and a source that can serve one is
    // the slow half of the list: an addon fronting several catalogues walks
    // all of them before it answers, which is seconds spent to land on a
    // transcode generic source already has at 320.
    AudioQuality.HIGH -> !kind.canServeLossless
    AudioQuality.MEDIUM, AudioQuality.LOW -> kind == SourceKind.YOUTUBE
}

