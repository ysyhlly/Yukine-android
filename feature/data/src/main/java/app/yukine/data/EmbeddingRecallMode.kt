package app.yukine.data

import app.yukine.data.room.SettingEntity
import app.yukine.data.room.SettingsDao

internal enum class EmbeddingRecallMode {
    OFF,
    SHADOW,
    ON
}

internal class EmbeddingRecallModeStore(private val settingsDao: SettingsDao) {
    fun mode(): EmbeddingRecallMode = runCatching {
        EmbeddingRecallMode.valueOf(settingsDao.value(KEY).orEmpty())
    }.getOrDefault(DEFAULT_MODE)

    fun setMode(mode: EmbeddingRecallMode) {
        settingsDao.put(SettingEntity(KEY, mode.name))
    }

    internal companion object {
        const val KEY = "recording_embedding_recall_mode"
        val DEFAULT_MODE = EmbeddingRecallMode.SHADOW
    }
}
