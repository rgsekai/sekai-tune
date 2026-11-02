package moe.rgsekai.sekaitune.shared.platform

expect fun normalizeNfd(text: String): String
expect fun normalizeNfkc(text: String): String
expect fun languageOf(tag: String): String
expect fun systemLanguageTag(): String
expect fun displayLanguageName(code: String, inTag: String): String
expect fun decimalSeparator(): Char

/** The full name of calendar month [month] (1..12) in the device language, e.g. "August". */
expect fun monthName(month: Int): String
