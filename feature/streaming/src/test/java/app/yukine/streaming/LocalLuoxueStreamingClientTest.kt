package app.yukine.streaming

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LocalLuoxueStreamingClientTest {
    @Test
    fun searchAggregatesKuwoAndKugouAsLuoxueSources() {
        val client = LocalLuoxueStreamingClient(
            httpClient = FakeLuoxueHttpClient(
                mapOf(
                    "search.kuwo.cn" to JSONObject()
                        .put(
                            "abslist",
                            JSONArray().put(
                                JSONObject()
                                    .put("DC_TARGETID", "101")
                                    .put("NAME", "Kuwo Song")
                                    .put("ARTIST", "Kuwo Artist")
                                    .put("web_albumpic_short", "120/s4s69/38/116989308.jpg")
                                    .put("FORMATS", "MP3128;MP3H;ALFLAC")
                            )
                        )
                        .toString(),
                    "mobiles.kugou.com/api/v3/search/song" to JSONObject()
                        .put(
                            "data",
                            JSONObject()
                                .put(
                                    "info",
                                    JSONArray().put(
                                        JSONObject()
                                            .put("hash", "0123456789abcdef0123456789abcdef")
                                            .put("album_id", 22)
                                            .put("album_audio_id", 33)
                                            .put("songname", "Kugou Song")
                                            .put("singername", "Kugou Artist")
                                            .put(
                                                "trans_param",
                                                JSONObject().put(
                                                    "union_cover",
                                                    "http://imge.kugou.com/stdmusic/{size}/cover.jpg"
                                                )
                                            )
                                            .put("HQFileHash", "abcdef0123456789")
                                    )
                                )
                        )
                        .toString()
                )
            )
        )

        val result = client.search(StreamingSearchRequest(StreamingProviderName.LUOXUE, "hello", pageSize = 10))

        assertEquals(2, result.tracks.size)
        assertEquals("kw:101", result.tracks[0].providerTrackId)
        assertEquals(
            "https://img1.kuwo.cn/star/albumcover/500/s4s69/38/116989308.jpg",
            result.tracks[0].coverUrl
        )
        assertEquals("kg:0123456789abcdef0123456789abcdef.22.33", result.tracks[1].providerTrackId)
        assertEquals("https://imge.kugou.com/stdmusic/400/cover.jpg", result.tracks[1].coverUrl)
        assertTrue(result.tracks[1].playbackCandidates.any { it.label == "LX/酷狗" })
    }

    @Test
    fun sourcePrefixSearchLimitsToRequestedSource() {
        val fake = FakeLuoxueHttpClient(
            mapOf(
                "search.kuwo.cn" to JSONObject().put("abslist", JSONArray()).toString(),
                "mobiles.kugou.com/api/v3/search/song" to JSONObject()
                    .put(
                        "data",
                        JSONObject().put(
                            "info",
                            JSONArray().put(
                                JSONObject()
                                    .put("hash", "abcdefabcdefabcdefabcdefabcdef12")
                                    .put("songname", "Only Kugou")
                            )
                        )
                    )
                    .toString()
            )
        )
        val client = LocalLuoxueStreamingClient(fake)

        val result = client.search(StreamingSearchRequest(StreamingProviderName.LUOXUE, "kg:hello"))

        assertEquals(1, result.tracks.size)
        assertEquals("Only Kugou", result.tracks.single().title)
        assertTrue(fake.urls.none { it.contains("search.kuwo.cn") })
    }

    @Test
    fun miguPrefixSearchKeepsCopyrightIdAndRawMusicInfoForScriptPlayback() {
        val response = JSONObject()
            .put("code", "000000")
            .put(
                "songResultData",
                JSONObject().put(
                    "resultList",
                    JSONArray().put(
                        JSONObject()
                            .put("name", "NPC")
                            .put("id", "1142344865")
                            .put("copyrightId", "69918308621")
                            .put("singers", JSONArray().put(JSONObject().put("id", "artist-1").put("name", "曜一")))
                            .put("albums", JSONArray().put(JSONObject().put("id", "album-1").put("name", "在世界边缘守候")))
                            .put("imgItems", JSONArray().put(JSONObject().put("img", "https://migu.example.test/cover.webp")))
                            .put(
                                "newRateFormats",
                                JSONArray()
                                    .put(JSONObject().put("formatType", "PQ"))
                                    .put(JSONObject().put("formatType", "HQ"))
                                    .put(JSONObject().put("formatType", "SQ"))
                                    .put(JSONObject().put("formatType", "ZQ"))
                            )
                    )
                )
            )
        val client = LocalLuoxueStreamingClient(
            httpClient = FakeLuoxueHttpClient(mapOf("content/search_all.do" to response.toString()))
        )

        val result = client.search(StreamingSearchRequest(StreamingProviderName.LUOXUE, "mg:NPC"))

        val track = result.tracks.single()
        assertEquals("mg:69918308621", track.providerTrackId)
        assertEquals("NPC", track.title)
        assertEquals("曜一", track.artist)
        assertEquals("在世界边缘守候", track.album)
        assertTrue(track.qualities.containsAll(StreamingAudioQuality.entries))
        assertEquals("69918308621", JSONObject(track.luoxueMusicInfoJson.orEmpty()).getString("copyrightId"))
    }

    @Test
    fun importedSearchTriesEveryDeclaredStandardAndExtensionSource() = runTest {
        val searchedKeys = mutableListOf<String>()
        val runtime = FakeScriptRuntime(
            searchResolver = { _, sourceKey, _, _, _ ->
                searchedKeys += sourceKey
                LuoxueScriptSearchPage(
                    items = (1..2).map { index ->
                        val idField = when (sourceKey) {
                            "kw" -> "DC_TARGETID"
                            "kg" -> "hash"
                            "tx" -> "songmid"
                            "mg" -> "copyrightId"
                            else -> "id"
                        }
                        mapOf(
                            "source" to sourceKey,
                            idField to "id-$sourceKey-$index",
                            "name" to "Song $sourceKey $index",
                            "artist" to "Artist"
                        )
                    }
                )
            },
            resolver = { _, _, _, _ -> "https://script.example.test/song.mp3" }
        )
        val client = LocalLuoxueStreamingClient(
            httpClient = FakeLuoxueHttpClient(emptyMap()),
            scriptRuntime = runtime
        )
        val sourceKinds = listOf("kw", "kg", "tx", "wy", "mg", "git", "local")

        val result = client.search(
            StreamingSearchRequest(StreamingProviderName.LUOXUE, "NPC", pageSize = 10),
            listOf(
                LuoxueImportedSource(
                    id = "all-sources",
                    name = "全平台脚本",
                    sourceKinds = sourceKinds,
                    script = "actions: ['search']"
                )
            )
        )

        assertEquals(sourceKinds, searchedKeys)
        assertEquals(10, result.tracks.size)
        assertEquals(
            sourceKinds + sourceKinds.take(3),
            result.tracks.map { it.providerTrackId.substringBefore(':') }
        )
        assertTrue(result.tracks.any { it.providerTrackId == "git:id-git-1" })
    }

    @Test
    fun importedQqSearchResultKeepsRawMusicInfoForScriptPlayback() = runTest {
        val searchedKeys = mutableListOf<String>()
        val runtime = FakeScriptRuntime(
            searchResolver = { _, sourceKey, query, page, pageSize ->
                searchedKeys += sourceKey
                assertEquals("周杰伦", query)
                assertEquals(1, page)
                assertEquals(10, pageSize)
                if (sourceKey != "tx") {
                    null
                } else {
                    LuoxueScriptSearchPage(
                        items = listOf(
                            mapOf(
                                "songmid" to "0039MnYb0qxYhV",
                                "name" to "QQ Script Song",
                                "singer" to listOf(mapOf("name" to "QQ Artist")),
                                "album" to mapOf("mid" to "004AlbumMid", "name" to "QQ Album"),
                                "file" to mapOf("media_mid" to "001MediaMid"),
                                "interval" to 245,
                                "size128" to 4_000_000,
                                "size320" to 9_000_000
                            )
                        ),
                        total = 1,
                        hasMore = false
                    )
                }
            },
            resolver = { _, sourceKey, musicInfo, quality ->
                assertEquals("tx", sourceKey)
                assertEquals("0039MnYb0qxYhV", musicInfo["songmid"])
                assertEquals("001MediaMid", musicInfo["mediaMid"])
                assertEquals("flac", quality)
                "https://script.example.test/qq-song.flac"
            }
        )
        val client = LocalLuoxueStreamingClient(scriptRuntime = runtime)
        val imported = LuoxueImportedSource(
            id = "qq-search-source",
            name = "QQ 搜索脚本",
            sourceKinds = listOf("tx"),
            script = "actions: ['search', 'musicUrl']"
        )

        val result = client.search(
            StreamingSearchRequest(
                provider = StreamingProviderName.LUOXUE,
                query = "tx:周杰伦",
                pageSize = 10
            ),
            listOf(imported)
        )

        assertEquals(listOf("tx"), searchedKeys)
        val track = result.tracks.single()
        assertEquals("tx:0039MnYb0qxYhV|001MediaMid", track.providerTrackId)
        assertEquals("QQ Script Song", track.title)
        assertEquals("QQ Artist", track.artist)
        assertEquals("QQ Album", track.album)
        assertEquals(245_000L, track.durationMs)
        assertTrue(track.coverUrl.orEmpty().contains("004AlbumMid"))
        val rawMusicInfo = JSONObject(track.luoxueMusicInfoJson.orEmpty())
        assertEquals("001MediaMid", rawMusicInfo.getJSONObject("file").getString("media_mid"))

        val playback = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.LUOXUE,
                providerTrackId = track.providerTrackId,
                quality = StreamingAudioQuality.LOSSLESS,
                luoxueMusicInfoJson = track.luoxueMusicInfoJson
            ),
            listOf(imported)
        )

        assertEquals("https://script.example.test/qq-song.flac", playback.url)
    }

    @Test
    fun playbackOnlyImportedScriptKeepsBuiltInSearchFallback() = runTest {
        val client = LocalLuoxueStreamingClient(
            httpClient = FakeLuoxueHttpClient(
                mapOf(
                    "search.kuwo.cn" to JSONObject()
                        .put(
                            "abslist",
                            JSONArray().put(
                                JSONObject()
                                    .put("DC_TARGETID", "101")
                                    .put("NAME", "Built-in Kuwo Result")
                            )
                        )
                        .toString()
                )
            ),
            scriptRuntime = FakeScriptRuntime(
                searchResolver = { _, _, _, _, _ -> error("search must not be invoked") },
                resolver = { _, _, _, _ -> "https://script.example.test/song.mp3" }
            )
        )
        val playbackOnly = LuoxueImportedSource(
            id = "playback-only",
            name = "传统播放脚本",
            sourceKinds = listOf("kw"),
            script = "actions: ['musicUrl']"
        )

        val result = client.search(
            StreamingSearchRequest(StreamingProviderName.LUOXUE, "kw:hello"),
            listOf(playbackOnly)
        )

        assertEquals("Built-in Kuwo Result", result.tracks.single().title)
    }

    @Test
    fun kugouPlaybackCollectsNestedUrl() {
        val hash = "0123456789abcdef0123456789abcdef"
        val client = LocalLuoxueStreamingClient(
            httpClient = FakeLuoxueHttpClient(
                mapOf(
                    "gateway.kugou.com/v5/url" to JSONObject()
                        .put(
                            "data",
                            JSONObject()
                                .put("play_url", "https://kugou.example.test/song.flac")
                        )
                        .toString()
                )
            )
        )

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.LUOXUE,
                providerTrackId = "kg:$hash.22.33",
                quality = StreamingAudioQuality.LOSSLESS
            )
        )

        assertEquals(StreamingProviderName.LUOXUE, source.provider)
        assertEquals("kg:$hash.22.33", source.providerTrackId)
        assertEquals("https://kugou.example.test/song.flac", source.url)
        assertEquals("audio/flac", source.mimeType)
    }

    @Test
    fun importedSourceResolvesPlaybackBeforeBuiltInFallback() = runTest {
        val runtime = FakeScriptRuntime { _, sourceKey, musicInfo, quality ->
            assertEquals("kg", sourceKey)
            assertEquals("0123456789abcdef0123456789abcdef", musicInfo["hash"])
            assertEquals("22", musicInfo["album_id"])
            assertEquals("33", musicInfo["album_audio_id"])
            assertEquals("flac", quality)
            "https://script.example.test/music.flac"
        }
        val client = LocalLuoxueStreamingClient(scriptRuntime = runtime)

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.LUOXUE,
                providerTrackId = "kg:0123456789abcdef0123456789abcdef.22.33",
                quality = StreamingAudioQuality.LOSSLESS
            ),
            listOf(
                LuoxueImportedSource(
                    id = "script-1",
                    name = "测试脚本",
                    sourceKinds = listOf("kg"),
                    script = "ignored by fake"
                )
            )
        )

        assertEquals("https://script.example.test/music.flac", source.url)
        assertEquals("audio/flac", source.mimeType)
    }

    @Test
    fun importedSourceDowngradesQualityWhenHiResUrlIsUnavailable() = runTest {
        val attemptedQualities = mutableListOf<String>()
        val client = LocalLuoxueStreamingClient(
            scriptRuntime = FakeScriptRuntime { _, sourceKey, _, quality ->
                assertEquals("kg", sourceKey)
                attemptedQualities += quality
                if (quality == "flac24bit") {
                    "Error: upstream returned 500"
                } else {
                    "http://script.example.test/music.flac"
                }
            }
        )

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.LUOXUE,
                providerTrackId = "kg:3cdd57a2b1af8f9eaedde3047648c3a0.1.2",
                quality = StreamingAudioQuality.HIRES
            ),
            listOf(LuoxueImportedSource("script-1", "酷狗脚本", sourceKinds = listOf("kg")))
        )

        assertEquals(listOf("flac24bit", "flac"), attemptedQualities)
        assertEquals("http://script.example.test/music.flac", source.url)
        assertEquals(900, source.bitrate)
    }

    @Test
    fun importedPlaybackScriptFallsBackToLocalActionWhenNativeSubSourceIsMissing() = runTest {
        val attemptedKeys = mutableListOf<String>()
        val client = LocalLuoxueStreamingClient(
            scriptRuntime = FakeScriptRuntime(
                resolver = { _, sourceKey, _, _ ->
                    attemptedKeys += sourceKey
                    if (sourceKey == "local") {
                        "https://script.example.test/local-song.mp3"
                    } else {
                        throw StreamingGatewayException(
                            "native sub-source is unavailable",
                            code = StreamingErrorCode.SOURCE_UNAVAILABLE
                        )
                    }
                }
            )
        )
        val source = LuoxueImportedSource(
            id = "local-playback-script",
            name = "本地子源播放脚本",
            sourceKinds = listOf("kw", "local"),
            script = "ignored by fake"
        )

        val resolved = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.LUOXUE,
                providerTrackId = "kw:98765"
            ),
            listOf(source)
        )

        assertEquals("https://script.example.test/local-song.mp3", resolved.url)
        assertEquals(listOf("kw", "local"), attemptedKeys)
    }

    @Test
    fun importedSourceReceivesCompletePersistedLuoxueMusicInfo() = runTest {
        val runtime = FakeScriptRuntime { _, sourceKey, musicInfo, _ ->
            assertEquals("kg", sourceKey)
            assertEquals("0123456789abcdef0123456789abcdef", musicInfo["hash"])
            assertEquals("preserved", (musicInfo["nested"] as Map<*, *>)["label"])
            assertEquals(listOf("one", "two"), musicInfo["aliases"])
            "https://script.example.test/full-context.flac"
        }
        val client = LocalLuoxueStreamingClient(scriptRuntime = runtime)
        val musicInfo = """
            {
              "hash":"0123456789abcdef0123456789abcdef",
              "album_id":"22",
              "album_audio_id":"33",
              "nested":{"label":"preserved"},
              "aliases":["one","two"]
            }
        """.trimIndent()

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.LUOXUE,
                providerTrackId = "kg:0123456789abcdef0123456789abcdef.22.33",
                luoxueMusicInfoJson = musicInfo
            ),
            listOf(LuoxueImportedSource("script-1", "完整信息脚本", sourceKinds = listOf("kg")))
        )

        assertEquals("https://script.example.test/full-context.flac", source.url)
    }

    @Test
    fun importedSourceFailureFallsBackToBuiltInResolver() = runTest {
        val hash = "0123456789abcdef0123456789abcdef"
        val client = LocalLuoxueStreamingClient(
            httpClient = FakeLuoxueHttpClient(
                mapOf(
                    "gateway.kugou.com/v5/url" to JSONObject()
                        .put("data", JSONObject().put("play_url", "https://kugou.example.test/fallback.flac"))
                        .toString()
                )
            ),
            scriptRuntime = FakeScriptRuntime { _, _, _, _ ->
                throw StreamingGatewayException("脚本不可用", code = StreamingErrorCode.SOURCE_UNAVAILABLE)
            }
        )

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.LUOXUE,
                providerTrackId = "kg:$hash.22.33",
                quality = StreamingAudioQuality.LOSSLESS
            ),
            listOf(LuoxueImportedSource("script-1", "失效脚本", sourceKinds = listOf("kg")))
        )

        assertEquals("https://kugou.example.test/fallback.flac", source.url)
    }

    @Test
    fun shiqianjiangKugouSourceUsesScopedCompatibilityEndpointWhenScriptFails() = runTest {
        val hash = "0123456789abcdef0123456789abcdef"
        val http = FakeLuoxueHttpClient(
            mapOf(
                "source.shiqianjiang.cn/api/music/url" to JSONObject()
                    .put("code", 200)
                    .put("url", "https://media.example.test/kg-song.flac")
                    .toString()
            )
        )
        val client = LocalLuoxueStreamingClient(
            httpClient = http,
            scriptRuntime = FakeScriptRuntime { _, _, _, _ ->
                throw IllegalStateException("链接获取失败,数字专辑")
            }
        )

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.LUOXUE,
                providerTrackId = "kg:$hash.22.33",
                quality = StreamingAudioQuality.LOSSLESS
            ),
            listOf(
                LuoxueImportedSource(
                    id = "script-1",
                    name = "测试脚本",
                    origin = "https://source.shiqianjiang.cn/api/script/lx?key=test-key",
                    sourceKinds = listOf("kg")
                )
            )
        )

        assertEquals("https://media.example.test/kg-song.flac", source.url)
        assertTrue(http.urls.single().contains("songId=$hash"))
        assertTrue(http.urls.single().contains("key=test-key"))
    }

    @Test
    fun reportsScriptFailureWhenBuiltInResolverAlsoFails() = runTest {
        val client = LocalLuoxueStreamingClient(
            scriptRuntime = FakeScriptRuntime { _, sourceKey, _, _ ->
                throw StreamingGatewayException(
                    "远端接口返回 403",
                    code = StreamingErrorCode.SOURCE_UNAVAILABLE
                )
            }
        )

        try {
            client.resolvePlayback(
                StreamingPlaybackRequest(
                    provider = StreamingProviderName.LUOXUE,
                    providerTrackId = "mg:copyright-1"
                ),
                listOf(LuoxueImportedSource("script-1", "测试脚本", sourceKinds = listOf("mg")))
            )
            fail("Expected LX playback resolution to fail")
        } catch (error: StreamingGatewayException) {
            assertTrue(error.message.orEmpty().contains("测试脚本/mg"))
            assertTrue(error.message.orEmpty().contains("远端接口返回 403"))
            assertTrue(error.message.orEmpty().contains("本机兜底也不可用"))
        }
    }

    @Test
    fun scriptFailureHidesOriginCredentialAndCollapsesDigitalAlbumErrors() = runTest {
        val client = LocalLuoxueStreamingClient(
            scriptRuntime = FakeScriptRuntime { _, _, _, _ ->
                throw IllegalStateException(
                    "链接获取失败,数字专辑\n" +
                        "at handleGetMusicUrl (https://source.example/api/script/lx?key=secret-value:38)"
                )
            }
        )

        try {
            client.resolvePlayback(
                StreamingPlaybackRequest(
                    provider = StreamingProviderName.LUOXUE,
                    providerTrackId = "mg:copyright-1"
                ),
                listOf(LuoxueImportedSource("script-1", "测试脚本", sourceKinds = listOf("mg")))
            )
            fail("Expected LX playback resolution to fail")
        } catch (error: StreamingGatewayException) {
            val message = error.message.orEmpty()
            assertTrue(message.contains("该数字专辑暂不支持此音源"))
            assertFalse(message.contains("secret-value"))
            assertFalse(message.contains("handleGetMusicUrl"))
            assertEquals(1, Regex("该数字专辑暂不支持此音源").findAll(message).count())
        }
    }

    @Test
    fun importedSharedSourceGetsGenericIdAliasesForKuwo() = runTest {
        val client = LocalLuoxueStreamingClient(
            scriptRuntime = FakeScriptRuntime { _, sourceKey, musicInfo, _ ->
                assertEquals("kw", sourceKey)
                assertEquals("98765", musicInfo["id"])
                assertEquals("98765", musicInfo["hash"])
                assertEquals("98765", musicInfo["songmid"])
                assertEquals("98765", musicInfo["rid"])
                "https://script.example.test/kuwo.mp3"
            }
        )

        val source = client.resolvePlayback(
            StreamingPlaybackRequest(
                provider = StreamingProviderName.LUOXUE,
                providerTrackId = "kw:98765"
            ),
            listOf(LuoxueImportedSource("script-1", "共享脚本", sourceKinds = listOf("kw")))
        )

        assertEquals("https://script.example.test/kuwo.mp3", source.url)
    }

    @Test
    fun importedSourceCancellationIsNotConvertedIntoFallback() = runTest {
        val client = LocalLuoxueStreamingClient(
            scriptRuntime = FakeScriptRuntime { _, _, _, _ ->
                throw CancellationException("playback request cancelled")
            }
        )

        try {
            client.resolvePlayback(
                StreamingPlaybackRequest(
                    provider = StreamingProviderName.LUOXUE,
                    providerTrackId = "kg:0123456789abcdef0123456789abcdef.22.33"
                ),
                listOf(LuoxueImportedSource("script-1", "取消脚本", sourceKinds = listOf("kg")))
            )
            fail("Expected playback cancellation to propagate")
        } catch (_: CancellationException) {
            // Expected: a stale playback request must never continue into the native fallback.
        }
    }

    @Test
    fun importedLocalSourceResolvesLyricsAndArtworkWithPersistedMusicInfo() = runTest {
        val runtime = FakeScriptRuntime(
            resolver = { _, _, _, _ -> error("playback should not be requested") },
            lyricResolver = { _, sourceKey, musicInfo ->
                assertEquals("local", sourceKey)
                assertEquals("98765", musicInfo["rid"])
                assertEquals("kept", (musicInfo["nested"] as Map<*, *>)["label"])
                LuoxueScriptLyrics(
                    lyric = "[00:01.00]原文",
                    translation = "[00:01.00]Translation"
                )
            },
            coverResolver = { _, sourceKey, musicInfo ->
                assertEquals("local", sourceKey)
                assertEquals("98765", musicInfo["rid"])
                "https://script.example.test/cover.jpg"
            }
        )
        val client = LocalLuoxueStreamingClient(scriptRuntime = runtime)
        val source = LuoxueImportedSource(
            id = "metadata-script",
            name = "元数据脚本",
            sourceKinds = listOf("local"),
            script = "ignored by fake"
        )
        val musicInfo = """{"rid":"98765","nested":{"label":"kept"}}"""

        val lyrics = client.resolveLyrics("kw:98765", musicInfo, listOf(source))
        val cover = client.resolveCoverUrl("kw:98765", musicInfo, listOf(source))

        assertEquals("[00:01.00]原文", lyrics?.lyric)
        assertEquals("[00:01.00]Translation", lyrics?.translation)
        assertEquals("https://script.example.test/cover.jpg", cover)
    }

    @Test
    fun importedMetadataScriptFallsBackToLocalActionAfterNativeSubSource() = runTest {
        val attemptedKeys = mutableListOf<String>()
        val client = LocalLuoxueStreamingClient(
            scriptRuntime = FakeScriptRuntime(
                lyricResolver = { _, sourceKey, _ ->
                    attemptedKeys += sourceKey
                    if (sourceKey == "local") {
                        LuoxueScriptLyrics("[00:01.00]local lyric")
                    } else {
                        null
                    }
                },
                resolver = { _, _, _, _ -> error("playback should not be requested") }
            )
        )
        val source = LuoxueImportedSource(
            id = "shared-metadata-script",
            name = "共享元数据脚本",
            sourceKinds = listOf("kw", "local"),
            script = "ignored by fake"
        )

        val lyrics = client.resolveLyrics("kw:98765", null, listOf(source))

        assertEquals("[00:01.00]local lyric", lyrics?.lyric)
        assertEquals(listOf("kw", "local"), attemptedKeys)
    }

    private class FakeLuoxueHttpClient(
        private val responses: Map<String, String>
    ) : LuoxueHttpClient {
        val urls = mutableListOf<String>()

        override fun getText(url: String, headers: Map<String, String>): String {
            urls += url
            return responses.entries.firstOrNull { (key, _) -> url.contains(key) }?.value
                ?: error("No fake response for $url")
        }
    }

    private class FakeScriptRuntime(
        private val searchResolver: suspend (
            LuoxueImportedSource,
            String,
            String,
            Int,
            Int
        ) -> LuoxueScriptSearchPage? = { _, _, _, _, _ -> null },
        private val lyricResolver: suspend (
            LuoxueImportedSource,
            String,
            Map<String, Any?>
        ) -> LuoxueScriptLyrics? = { _, _, _ -> null },
        private val coverResolver: suspend (
            LuoxueImportedSource,
            String,
            Map<String, Any?>
        ) -> String? = { _, _, _ -> null },
        private val resolver: suspend (
            LuoxueImportedSource,
            String,
            Map<String, Any?>,
            String
        ) -> String
    ) : LuoxueScriptRuntime {
        override suspend fun search(
            source: LuoxueImportedSource,
            sourceKey: String,
            query: String,
            page: Int,
            pageSize: Int
        ): LuoxueScriptSearchPage? = searchResolver(source, sourceKey, query, page, pageSize)

        override suspend fun resolveMusicUrl(
            source: LuoxueImportedSource,
            sourceKey: String,
            musicInfo: Map<String, Any?>,
            quality: String
        ): String = resolver(source, sourceKey, musicInfo, quality)

        override suspend fun resolveLyrics(
            source: LuoxueImportedSource,
            sourceKey: String,
            musicInfo: Map<String, Any?>
        ): LuoxueScriptLyrics? = lyricResolver(source, sourceKey, musicInfo)

        override suspend fun resolveCoverUrl(
            source: LuoxueImportedSource,
            sourceKey: String,
            musicInfo: Map<String, Any?>
        ): String? = coverResolver(source, sourceKey, musicInfo)
    }
}
