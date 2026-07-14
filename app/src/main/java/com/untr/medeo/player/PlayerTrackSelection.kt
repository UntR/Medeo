package com.untr.medeo.player

import java.util.Locale

internal fun findMatchingTrackLanguage(
    preferredLanguage: String?,
    availableLanguages: List<String?>
): Int? {
    val preferredTag = preferredLanguage.normalizedLanguageTag() ?: return null
    val exactMatch = availableLanguages.indexOfFirst { it.normalizedLanguageTag() == preferredTag }
    if (exactMatch >= 0) return exactMatch

    val preferredBase = Locale.forLanguageTag(preferredTag).language
    val baseMatch = availableLanguages.indexOfFirst {
        it.normalizedLanguageTag()?.let { tag -> Locale.forLanguageTag(tag).language } == preferredBase
    }
    return baseMatch.takeIf { it >= 0 }
}

internal fun String?.normalizedLanguageTag(): String? {
    val value = this?.trim()?.replace('_', '-')?.takeIf { it.isNotEmpty() } ?: return null
    val tag = Locale.forLanguageTag(value).toLanguageTag().lowercase(Locale.ROOT)
    return tag.takeUnless { it == "und" }
}
