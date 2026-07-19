package app.yukine.data

import app.yukine.data.room.SettingEntity
import app.yukine.data.room.SettingsDao
import app.yukine.identity.LibraryDedupMode

internal class LibraryDedupModeStore(private val settingsDao: SettingsDao) {
    fun mode(): LibraryDedupMode =
        LibraryDedupMode.fromStoredValue(settingsDao.value(KEY))

    fun generation(): Long =
        settingsDao.value(GENERATION_KEY)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L

    fun setMode(mode: LibraryDedupMode) {
        if (mode == mode()) return
        settingsDao.put(SettingEntity(KEY, mode.name))
        settingsDao.put(SettingEntity(GENERATION_KEY, (generation() + 1L).toString()))
    }

    internal companion object {
        const val KEY = "library_dedup_mode"
        const val GENERATION_KEY = "library_dedup_generation"
    }
}
