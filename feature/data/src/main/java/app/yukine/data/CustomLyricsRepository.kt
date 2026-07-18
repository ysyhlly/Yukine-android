package app.yukine.data

import android.content.Context
import app.yukine.common.StreamingDataPathParser
import app.yukine.data.room.CustomLyricsEntity
import app.yukine.data.room.SettingEntity
import app.yukine.data.room.YukineDatabase
import app.yukine.model.LyricsDocument
import app.yukine.model.Track
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class StoredCustomLyrics(
    val identityKey: String,
    val document: LyricsDocument,
    val sourceName: String,
    val format: String,
    val checksum: String,
    val updatedAt: Long
)

data class CustomLyricsRecordingWrite(
    val recordingId: Long,
    val document: LyricsDocument,
    val sourceName: String = document.sourceName,
    val format: String = document.format,
    val updatedAt: Long = System.currentTimeMillis()
)

data class LyricsDisplayPreferences(
    val primary: Boolean = true,
    val translation: Boolean = true,
    val romanization: Boolean = false
) {
    init {
        require(primary || translation || romanization) {
            "At least one lyrics track must remain visible"
        }
    }
}

data class CustomLyricsTarget(
    val recordingId: Long? = null,
    val provider: String = "",
    val providerTrackId: String = ""
) {
    fun isStable(): Boolean =
        recordingId?.let { it > 0L } == true ||
            (provider.isNotBlank() && providerTrackId.isNotBlank())
}

@Singleton
class CustomLyricsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val streamingDataPathParser: StreamingDataPathParser
) {
    private val database = YukineDatabase.getInstance(context.applicationContext)
    private val dao = database.musicIdentityDao()
    private val settingsDao = database.settingsDao()

    fun loadForTrack(track: Track): StoredCustomLyrics? {
        val identity = identityFor(track)
        if (identity.recordingId != null) {
            decode(dao.customLyrics(recordingKey(identity.recordingId)))?.let { return it }
        }
        if (identity.provider.isNotBlank() && identity.providerTrackId.isNotBlank()) {
            decode(dao.customLyricsForProvider(identity.provider, identity.providerTrackId))
                ?.let { return it }
        }
        val recordingId = identity.recordingId ?: return null
        return dao.sources(recordingId)
            .asSequence()
            .mapNotNull { source ->
                decode(dao.customLyricsForProvider(source.provider, source.providerTrackId))
            }
            .maxByOrNull(StoredCustomLyrics::updatedAt)
    }

    fun saveForTrack(
        track: Track,
        document: LyricsDocument,
        sourceName: String = document.sourceName,
        format: String = document.format,
        updatedAt: Long = System.currentTimeMillis()
    ): StoredCustomLyrics {
        require(!document.isEmpty()) { "Custom lyrics are empty" }
        val identity = identityFor(track)
        require(identity.recordingId != null || identity.provider.isNotBlank()) {
            "Track has no stable lyrics identity"
        }
        val key = identity.recordingId?.let(::recordingKey)
            ?: providerKey(identity.provider, identity.providerTrackId)
        val entity = entity(
            identityKey = key,
            recordingId = identity.recordingId,
            provider = if (identity.recordingId == null) identity.provider else "",
            providerTrackId = if (identity.recordingId == null) identity.providerTrackId else "",
            document = document,
            sourceName = sourceName,
            format = format,
            updatedAt = updatedAt
        )
        dao.upsert(entity)
        return requireNotNull(decode(entity))
    }

    fun targetForTrack(track: Track): CustomLyricsTarget {
        val identity = identityFor(track)
        return CustomLyricsTarget(
            recordingId = identity.recordingId,
            provider = identity.provider,
            providerTrackId = identity.providerTrackId
        )
    }

    fun hasForTarget(target: CustomLyricsTarget): Boolean {
        if (!target.isStable()) return false
        val stored = target.recordingId?.let { dao.customLyrics(recordingKey(it)) }
            ?: dao.customLyricsForProvider(
                target.provider.trim().lowercase(Locale.ROOT),
                target.providerTrackId.trim()
            )
        return decode(stored) != null
    }

    fun saveForTarget(
        target: CustomLyricsTarget,
        document: LyricsDocument,
        sourceName: String = document.sourceName,
        format: String = document.format,
        updatedAt: Long = System.currentTimeMillis()
    ): StoredCustomLyrics {
        require(target.isStable()) { "Track has no stable lyrics identity" }
        require(!document.isEmpty()) { "Custom lyrics are empty" }
        val provider = target.provider.trim().lowercase(Locale.ROOT)
        val providerTrackId = target.providerTrackId.trim()
        val key = target.recordingId?.let(::recordingKey)
            ?: providerKey(provider, providerTrackId)
        val saved = entity(
            identityKey = key,
            recordingId = target.recordingId,
            provider = if (target.recordingId == null) provider else "",
            providerTrackId = if (target.recordingId == null) providerTrackId else "",
            document = document,
            sourceName = sourceName,
            format = format,
            updatedAt = updatedAt
        )
        dao.upsert(saved)
        return requireNotNull(decode(saved))
    }

    fun saveForRecording(
        recordingId: Long,
        document: LyricsDocument,
        sourceName: String = document.sourceName,
        format: String = document.format,
        updatedAt: Long = System.currentTimeMillis(),
        overwrite: Boolean = false
    ): Boolean {
        require(recordingId > 0L)
        require(!document.isEmpty())
        val key = recordingKey(recordingId)
        if (!overwrite && dao.customLyrics(key) != null) return false
        dao.upsert(entity(
            identityKey = key,
            recordingId = recordingId,
            provider = "",
            providerTrackId = "",
            document = document,
            sourceName = sourceName,
            format = format,
            updatedAt = updatedAt
        ))
        return true
    }

    fun hasForRecording(recordingId: Long): Boolean =
        recordingId > 0L && dao.customLyrics(recordingKey(recordingId)) != null

    /**
     * Commits one fully prepared batch atomically. Existing rows are rechecked inside the
     * transaction and skipped so a concurrent single-song import is never overwritten.
     */
    fun saveBatchForRecordings(writes: List<CustomLyricsRecordingWrite>): Set<Long> {
        if (writes.isEmpty()) return emptySet()
        val saved = linkedSetOf<Long>()
        database.runInTransaction {
            writes.forEach { write ->
                require(write.recordingId > 0L)
                require(!write.document.isEmpty())
                val key = recordingKey(write.recordingId)
                if (dao.customLyrics(key) == null) {
                    dao.upsert(
                        entity(
                            identityKey = key,
                            recordingId = write.recordingId,
                            provider = "",
                            providerTrackId = "",
                            document = write.document,
                            sourceName = write.sourceName,
                            format = write.format,
                            updatedAt = write.updatedAt
                        )
                    )
                    saved += write.recordingId
                }
            }
        }
        return saved
    }

    fun loadDisplayPreferences(): LyricsDisplayPreferences {
        val primary = settingBoolean(KEY_PRIMARY_VISIBLE, true)
        val translation = settingBoolean(KEY_TRANSLATION_VISIBLE, true)
        val romanization = settingBoolean(KEY_ROMANIZATION_VISIBLE, false)
        return if (primary || translation || romanization) {
            LyricsDisplayPreferences(primary, translation, romanization)
        } else {
            val repaired = LyricsDisplayPreferences()
            saveDisplayPreferences(repaired)
            repaired
        }
    }

    fun saveDisplayPreferences(preferences: LyricsDisplayPreferences) {
        settingsDao.putAll(
            listOf(
                SettingEntity(KEY_PRIMARY_VISIBLE, preferences.primary.toString()),
                SettingEntity(KEY_TRANSLATION_VISIBLE, preferences.translation.toString()),
                SettingEntity(KEY_ROMANIZATION_VISIBLE, preferences.romanization.toString())
            )
        )
    }

    fun deleteForTrack(track: Track): Boolean {
        val identity = identityFor(track)
        var deleted = 0
        identity.recordingId?.let { recordingId ->
            deleted += dao.deleteCustomLyrics(recordingKey(recordingId))
            dao.sources(recordingId).forEach { source ->
                deleted += dao.deleteCustomLyricsForProvider(source.provider, source.providerTrackId)
            }
        }
        if (identity.provider.isNotBlank() && identity.providerTrackId.isNotBlank()) {
            deleted += dao.deleteCustomLyricsForProvider(identity.provider, identity.providerTrackId)
        }
        return deleted > 0
    }

    fun recordingIdForTrack(track: Track): Long? = identityFor(track).recordingId

    private fun identityFor(track: Track): LyricsIdentity {
        val localRecording = dao.recordingIdForLocalTrack(track.id)
        if (localRecording != null) return LyricsIdentity(localRecording, "", "")
        if (!streamingDataPathParser.isStreamingTrack(track.dataPath)) {
            return LyricsIdentity(null, "", "")
        }
        val provider = streamingDataPathParser.providerName(track.dataPath).orEmpty()
            .trim()
            .lowercase(Locale.ROOT)
        val providerTrackId = streamingDataPathParser.providerTrackId(track.dataPath).trim()
        val sourceRecording = if (provider.isNotBlank() && providerTrackId.isNotBlank()) {
            dao.source(provider, providerTrackId)?.recordingId
        } else {
            null
        }
        return LyricsIdentity(sourceRecording, provider, providerTrackId)
    }

    private fun entity(
        identityKey: String,
        recordingId: Long?,
        provider: String,
        providerTrackId: String,
        document: LyricsDocument,
        sourceName: String,
        format: String,
        updatedAt: Long
    ): CustomLyricsEntity {
        val encoded = LyricsDocumentJsonCodec.encode(document)
        return CustomLyricsEntity(
            identityKey = identityKey,
            recordingId = recordingId,
            provider = provider,
            providerTrackId = providerTrackId,
            sourceName = sourceName.trim(),
            format = format.trim().lowercase(Locale.ROOT),
            documentJson = encoded,
            checksum = sha256(encoded),
            updatedAt = updatedAt.coerceAtLeast(0L)
        )
    }

    private fun decode(entity: CustomLyricsEntity?): StoredCustomLyrics? {
        entity ?: return null
        return runCatching {
            StoredCustomLyrics(
                identityKey = entity.identityKey,
                document = LyricsDocumentJsonCodec.decode(entity.documentJson),
                sourceName = entity.sourceName,
                format = entity.format,
                checksum = entity.checksum,
                updatedAt = entity.updatedAt
            )
        }.getOrNull()
    }

    private fun recordingKey(recordingId: Long): String = "recording:$recordingId"

    private fun providerKey(provider: String, providerTrackId: String): String =
        "streaming:${provider.trim().lowercase(Locale.ROOT)}:" +
            URLEncoder.encode(providerTrackId.trim(), StandardCharsets.UTF_8.name())

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class LyricsIdentity(
        val recordingId: Long?,
        val provider: String,
        val providerTrackId: String
    )

    private fun settingBoolean(key: String, fallback: Boolean): Boolean =
        settingsDao.value(key)?.trim()?.toBooleanStrictOrNull() ?: fallback

    private companion object {
        const val KEY_PRIMARY_VISIBLE = "lyrics_track_primary_visible"
        const val KEY_TRANSLATION_VISIBLE = "lyrics_track_translation_visible"
        const val KEY_ROMANIZATION_VISIBLE = "lyrics_track_romanization_visible"
    }
}
