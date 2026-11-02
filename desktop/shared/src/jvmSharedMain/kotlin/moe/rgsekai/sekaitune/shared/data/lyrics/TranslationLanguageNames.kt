package moe.rgsekai.sekaitune.shared.data.lyrics

import java.util.Locale

/** JVM callers hold a [Locale]; the common function takes its language tag. */
fun translationLanguageName(code: String, inLocale: Locale): String =
    translationLanguageName(code, inLocale.toLanguageTag())
