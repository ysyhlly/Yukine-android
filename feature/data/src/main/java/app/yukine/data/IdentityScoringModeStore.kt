package app.yukine.data

import app.yukine.data.room.SettingEntity
import app.yukine.data.room.SettingsDao
import app.yukine.streaming.IdentityScoringMode

internal class IdentityScoringModeStore(private val settingsDao: SettingsDao) {
    fun mode(): IdentityScoringMode =
        IdentityScoringMode.fromStoredValue(settingsDao.value(KEY))

    fun setMode(mode: IdentityScoringMode) {
        settingsDao.put(SettingEntity(KEY, mode.name))
    }

    internal companion object {
        const val KEY = "recording_identity_scoring_mode"
        val DEFAULT_MODE = IdentityScoringMode.V5_SHADOW
    }
}
