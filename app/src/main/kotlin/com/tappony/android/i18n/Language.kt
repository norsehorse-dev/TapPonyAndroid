package com.tappony.android.i18n

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The languages TapPony ships, for the in-app picker in Settings (same approach as PGPony).
 * [nativeName] is never localized: "Deutsch" reads "Deutsch" whatever the app's language is.
 */
enum class SupportedLanguage(val tag: String, val nativeName: String) {
    EN("en", "English"),
    DE("de", "Deutsch"),
    ES("es", "Español"),
    FR("fr", "Français"),
    PT_BR("pt-BR", "Português (Brasil)"),
    RU("ru", "Русский"),
    ZH_HANS("zh-Hans", "简体中文");

    companion object {
        /**
         * Snaps a language tag to a shipped language: "pt-PT" to Brazilian Portuguese,
         * "es-MX" to Spanish, "en-GB" to English. Traditional Chinese ("zh-Hant", "zh-TW",
         * "zh-HK", "zh-MO") resolves to nothing, so it keeps English rather than Simplified.
         */
        fun resolve(tag: String?): SupportedLanguage? {
            if (tag.isNullOrBlank()) return null
            val normalized = tag.replace('_', '-')
            entries.firstOrNull { it.tag.equals(normalized, ignoreCase = true) }?.let { return it }
            val parts = normalized.lowercase().split('-')
            return when (parts.first()) {
                "en" -> EN
                "de" -> DE
                "es" -> ES
                "fr" -> FR
                "pt" -> PT_BR
                "ru" -> RU
                "zh" -> {
                    val rest = parts.drop(1)
                    when {
                        "hans" in rest -> ZH_HANS
                        "hant" in rest || rest.any { it == "tw" || it == "hk" || it == "mo" } -> null
                        else -> ZH_HANS
                    }
                }
                else -> null
            }
        }
    }
}

object LanguageManager {
    /** The picked language, or the phone's language snapped to a shipped one when none was picked. */
    fun current(context: Context): SupportedLanguage {
        val picked = AppCompatDelegate.getApplicationLocales()
        if (!picked.isEmpty) SupportedLanguage.resolve(picked[0]?.toLanguageTag())?.let { return it }
        val system = context.resources.configuration.locales
        for (i in 0 until system.size()) SupportedLanguage.resolve(system[i].toLanguageTag())?.let { return it }
        return SupportedLanguage.EN
    }

    /** Persists the choice and recreates the activity in that language. */
    fun set(lang: SupportedLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))
    }
}
