package app.yukine.data

import app.yukine.model.LyricLine
import app.yukine.model.LyricWord
import app.yukine.model.LyricsDocument
import app.yukine.model.LyricsTrack
import app.yukine.model.LyricsTrackRole
import org.json.JSONArray
import org.json.JSONObject

internal object LyricsDocumentJsonCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(document: LyricsDocument): String = JSONObject()
        .put("version", SCHEMA_VERSION)
        .put("sourceName", document.sourceName)
        .put("format", document.format)
        .put("title", document.title)
        .put("artist", document.artist)
        .put("album", document.album)
        .put("tracks", JSONArray().apply {
            document.tracks.forEach { track ->
                put(JSONObject()
                    .put("role", track.role.name)
                    .put("language", track.languageTag)
                    .put("lines", JSONArray().apply {
                        track.lines.forEach { line ->
                            put(JSONObject()
                                .put("start", line.startMs)
                                .put("end", line.endMs)
                                .put("text", line.text)
                                .put("words", JSONArray().apply {
                                    line.words.forEach { word ->
                                        put(JSONObject()
                                            .put("start", word.startMs)
                                            .put("end", word.endMs)
                                            .put("text", word.text)
                                            .put("startOffset", word.startOffset)
                                            .put("endOffset", word.endOffset)
                                        )
                                    }
                                })
                            )
                        }
                    })
                )
            }
        })
        .toString()

    fun decode(value: String): LyricsDocument {
        val json = JSONObject(value)
        require(json.optInt("version", 0) == SCHEMA_VERSION) {
            "Unsupported lyrics document version"
        }
        val tracksJson = json.optJSONArray("tracks") ?: JSONArray()
        val tracks = buildList {
            for (trackIndex in 0 until tracksJson.length()) {
                val trackJson = tracksJson.optJSONObject(trackIndex) ?: continue
                val role = runCatching {
                    LyricsTrackRole.valueOf(trackJson.optString("role"))
                }.getOrDefault(LyricsTrackRole.PRIMARY)
                val linesJson = trackJson.optJSONArray("lines") ?: JSONArray()
                val lines = buildList {
                    for (lineIndex in 0 until linesJson.length()) {
                        val lineJson = linesJson.optJSONObject(lineIndex) ?: continue
                        val wordsJson = lineJson.optJSONArray("words") ?: JSONArray()
                        val words = buildList {
                            for (wordIndex in 0 until wordsJson.length()) {
                                val wordJson = wordsJson.optJSONObject(wordIndex) ?: continue
                                val start = wordJson.optLong("start").coerceAtLeast(0L)
                                val end = wordJson.optLong("end", start).coerceAtLeast(start)
                                add(LyricWord(
                                    startMs = start,
                                    endMs = end,
                                    text = wordJson.optString("text"),
                                    startOffset = wordJson.optInt("startOffset"),
                                    endOffset = wordJson.optInt("endOffset")
                                ))
                            }
                        }
                        val start = lineJson.optLong("start").coerceAtLeast(0L)
                        val end = lineJson.optLong("end", start).coerceAtLeast(start)
                        add(LyricLine(start, end, lineJson.optString("text"), words))
                    }
                }
                add(LyricsTrack(role, trackJson.optString("language"), lines))
            }
        }
        return LyricsDocument(
            sourceName = json.optString("sourceName"),
            format = json.optString("format"),
            title = json.optString("title"),
            artist = json.optString("artist"),
            album = json.optString("album"),
            tracks = tracks
        )
    }
}
