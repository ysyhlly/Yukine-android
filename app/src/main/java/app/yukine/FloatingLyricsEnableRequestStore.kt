package app.yukine

import android.content.Context

/**
 * Stores only the one-shot permission round-trip intent. It is deliberately
 * separate from the long-term enabled preference owned by MusicLibraryRepository.
 */
internal class FloatingLyricsEnableRequestStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun markPending() {
        preferences.edit().putBoolean(KEY_PENDING_ENABLE, true).apply()
    }

    fun consumeIfGranted(permissionGranted: Boolean): Boolean {
        if (!permissionGranted || !preferences.getBoolean(KEY_PENDING_ENABLE, false)) {
            return false
        }
        clear()
        return true
    }

    fun clear() {
        preferences.edit().remove(KEY_PENDING_ENABLE).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "floating_lyrics_runtime"
        const val KEY_PENDING_ENABLE = "pending_enable_after_permission"
    }
}
