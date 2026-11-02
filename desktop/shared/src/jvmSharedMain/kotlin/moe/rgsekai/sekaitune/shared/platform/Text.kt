package moe.rgsekai.sekaitune.shared.platform

import java.text.Normalizer
import java.util.Locale

actual fun normalizeNfd(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
actual fun normalizeNfkc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
actual fun languageOf(tag: String): String = Locale.forLanguageTag(tag).language
actual fun systemLanguageTag(): String = Locale.getDefault().toLanguageTag()
actual fun displayLanguageName(code: String, inTag: String): String =
    Locale.forLanguageTag(code).getDisplayName(Locale.forLanguageTag(inTag))
actual fun decimalSeparator(): Char = java.text.DecimalFormatSymbols.getInstance().decimalSeparator

actual fun monthName(month: Int): String =
    java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
