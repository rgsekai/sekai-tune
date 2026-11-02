package moe.rgsekai.sekaitune.shared.platform

expect fun sha1(data: ByteArray): ByteArray
expect fun sha256(data: ByteArray): ByteArray
expect fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray
