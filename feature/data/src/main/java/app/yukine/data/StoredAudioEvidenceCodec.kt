package app.yukine.data

import app.yukine.data.room.AudioFeatureEntity
import app.yukine.fingerprint.ChromaprintSegment
import app.yukine.fingerprint.TraditionalAudioEvidence
import org.json.JSONObject

/** Room JSON boundary for versioned cold audio evidence. */
internal object StoredAudioEvidenceCodec {
    private const val RAW_WORD_HEX_LENGTH = 8
    private const val MAXIMUM_RAW_WORDS_PER_SEGMENT = 4_096

    fun decode(entity: AudioFeatureEntity?): TraditionalAudioEvidence {
        if (entity == null) return TraditionalAudioEvidence()
        val segments = runCatching {
            val root = JSONObject(entity.chromaprint)
            val values = root.optJSONArray("segments") ?: return@runCatching emptyList()
            buildList {
                for (index in 0 until values.length()) {
                    val value = values.optJSONObject(index) ?: continue
                    val rawHex = value.optString("rawHex")
                    val words = decodeRawWords(rawHex) ?: continue
                    add(
                        ChromaprintSegment(
                            startMs = value.optLong("startMs").coerceAtLeast(0L),
                            durationMs = value.optLong("durationMs").coerceAtLeast(0L),
                            words = words
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
        return TraditionalAudioEvidence(entity.pcmHash, segments)
    }

    private fun decodeRawWords(rawHex: String): IntArray? {
        if (rawHex.isEmpty() || rawHex.length % RAW_WORD_HEX_LENGTH != 0) return null
        val count = rawHex.length / RAW_WORD_HEX_LENGTH
        if (count !in 1..MAXIMUM_RAW_WORDS_PER_SEGMENT) return null
        return runCatching {
            IntArray(count) { index ->
                rawHex.substring(
                    index * RAW_WORD_HEX_LENGTH,
                    (index + 1) * RAW_WORD_HEX_LENGTH
                ).toLong(16).toInt()
            }
        }.getOrNull()
    }
}
