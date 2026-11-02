package moe.rgsekai.sekaitune.shared.playback

internal actual fun <T> unmodifiableList(list: List<T>): List<T> = java.util.Collections.unmodifiableList(list)
