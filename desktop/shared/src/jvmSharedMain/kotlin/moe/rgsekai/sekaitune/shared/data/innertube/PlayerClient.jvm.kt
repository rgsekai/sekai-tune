package moe.rgsekai.sekaitune.shared.data.innertube

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

internal actual fun visionOsUserAgent(): String? =
    YoutubeParsingHelper.getVisionOsUserAgent(NewPipe.getPreferredLocalization())
