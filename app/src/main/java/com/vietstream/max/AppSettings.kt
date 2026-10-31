package com.vietstream.max

import android.content.Context
import androidx.compose.ui.graphics.Color

/**
 * Cấu hình dùng chung của VIETSTREAM MAX.
 * Lưu bằng SharedPreferences để giữ cài đặt sau khi đóng ứng dụng.
 */
object AppSettings {

    private const val PREFS_NAME = "vietstream_settings"

    private const val KEY_THEME = "app_theme"
    private const val KEY_ACCENT = "app_accent"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_PLAYER = "app_player"
    private const val KEY_AUDIO = "app_audio"

    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    const val PLAYER_INTERNAL = "internal"
    const val PLAYER_VLC = "vlc"

    const val AUDIO_AUTO = "auto"
    const val AUDIO_STEREO = "stereo"
    const val AUDIO_DOLBY = "dolby"
    const val AUDIO_SPATIAL = "spatial"

    private val supportedLanguages = setOf(
        "vi", "en", "zh", "ja", "ko", "th", "id", "fr", "es"
    )

    private const val DEFAULT_ACCENT = 0xFFE50914L

    data class Settings(
        val theme: String = THEME_SYSTEM,
        val accentArgb: Long = DEFAULT_ACCENT,
        val language: String = "vi",
        val player: String = PLAYER_INTERNAL,
        val audioMode: String = AUDIO_AUTO
    )

    fun read(context: Context): Settings {
        val prefs = prefs(context)

        val theme = prefs.getString(KEY_THEME, THEME_SYSTEM)
            .orDefault(THEME_SYSTEM)
            .takeIf {
                it in setOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK)
            } ?: THEME_SYSTEM

        val accent = prefs.getLong(
            KEY_ACCENT,
            DEFAULT_ACCENT
        ).coerceIn(0L, 0xFFFFFFFFL)

        val language = prefs.getString(KEY_LANGUAGE, "vi")
            .orDefault("vi")
            .takeIf { it in supportedLanguages } ?: "vi"

        val player = prefs.getString(KEY_PLAYER, PLAYER_INTERNAL)
            .orDefault(PLAYER_INTERNAL)
            .takeIf {
                it in setOf(PLAYER_INTERNAL, PLAYER_VLC)
            } ?: PLAYER_INTERNAL

        val audioMode = prefs.getString(KEY_AUDIO, AUDIO_AUTO)
            .orDefault(AUDIO_AUTO)
            .takeIf {
                it in setOf(AUDIO_AUTO, AUDIO_STEREO, AUDIO_DOLBY, AUDIO_SPATIAL)
            } ?: AUDIO_AUTO

        return Settings(
            theme = theme,
            accentArgb = accent,
            language = language,
            player = player,
            audioMode = audioMode
        )
    }

    fun saveTheme(context: Context, value: String) {
        if (value !in setOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK)) return

        prefs(context).edit()
            .putString(KEY_THEME, value)
            .apply()
    }

    fun saveAccent(context: Context, argb: Long) {
        prefs(context).edit()
            .putLong(KEY_ACCENT, argb.coerceIn(0L, 0xFFFFFFFFL))
            .apply()
    }

    fun saveLanguage(context: Context, value: String) {
        if (value !in supportedLanguages) return

        prefs(context).edit()
            .putString(KEY_LANGUAGE, value)
            .apply()
    }

    fun savePlayer(context: Context, value: String) {
        if (value !in setOf(PLAYER_INTERNAL, PLAYER_VLC)) return

        prefs(context).edit()
            .putString(KEY_PLAYER, value)
            .apply()
    }

    fun saveAudioMode(context: Context, value: String) {
        if (value !in setOf(AUDIO_AUTO, AUDIO_STEREO, AUDIO_DOLBY, AUDIO_SPATIAL)) return

        prefs(context).edit()
            .putString(KEY_AUDIO, value)
            .apply()
    }

    fun accentColor(context: Context): Color {
        return Color(read(context).accentArgb.toULong())
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun String?.orDefault(default: String): String =
        this ?: default
}