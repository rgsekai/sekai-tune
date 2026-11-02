@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package moe.rgsekai.sekaitune.shared.platform

import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleIdentifier
import platform.Foundation.currentLocale
import platform.Foundation.decimalSeparator
import platform.Foundation.decomposedStringWithCanonicalMapping
import platform.Foundation.languageCode
import platform.Foundation.precomposedStringWithCompatibilityMapping
import platform.Foundation.preferredLanguages

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun normalizeNfd(text: String): String = (text as platform.Foundation.NSString).decomposedStringWithCanonicalMapping

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun normalizeNfkc(text: String): String = (text as platform.Foundation.NSString).precomposedStringWithCompatibilityMapping

actual fun languageOf(tag: String): String = NSLocale(localeIdentifier = tag).languageCode

actual fun systemLanguageTag(): String = (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en"

actual fun displayLanguageName(code: String, inTag: String): String =
    NSLocale(localeIdentifier = inTag).displayNameForKey(NSLocaleIdentifier, code) ?: code

actual fun decimalSeparator(): Char = NSLocale.currentLocale.decimalSeparator.firstOrNull() ?: '.'

actual fun monthName(month: Int): String =
    (platform.Foundation.NSDateFormatter().monthSymbols.getOrNull(month - 1) as? String) ?: month.toString()
