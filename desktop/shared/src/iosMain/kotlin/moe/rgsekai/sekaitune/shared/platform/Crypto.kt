@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package moe.rgsekai.sekaitune.shared.platform

import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.kCCAlgorithmDES
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCOptionECBMode
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import platform.posix.size_tVar
import platform.CoreCrypto.CC_SHA1
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.kCCHmacAlgSHA256

// Pad so addressOf(0) is legal on empty input; the length passed is still the real one.
private fun ByteArray.safe() = if (isEmpty()) ByteArray(1) else this

actual fun sha1(data: ByteArray): ByteArray = ByteArray(CC_SHA1_DIGEST_LENGTH).also { out ->
    out.usePinned { o -> data.safe().usePinned { CC_SHA1(it.addressOf(0), data.size.convert(), o.addressOf(0).reinterpret<UByteVar>()) } }
}

actual fun sha256(data: ByteArray): ByteArray = ByteArray(CC_SHA256_DIGEST_LENGTH).also { out ->
    out.usePinned { o -> data.safe().usePinned { CC_SHA256(it.addressOf(0), data.size.convert(), o.addressOf(0).reinterpret<UByteVar>()) } }
}
