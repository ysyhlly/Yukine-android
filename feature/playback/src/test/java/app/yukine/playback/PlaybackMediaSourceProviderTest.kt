package app.yukine.playback

import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import app.yukine.common.StreamingDataPathParser
import app.yukine.data.MusicLibraryRepository
import app.yukine.model.Track
import app.yukine.playback.manager.PlaybackMediaSourceProvider
import app.yukine.playback.manager.PlaybackQueueManager
import app.yukine.streaming.StreamingPlaybackHeaderStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PlaybackMediaSourceProviderTest {
    @Test
    fun providersReuseApplicationPlaybackConnectionPool() {
        val first = provider(FakeStreamingPlaybackHeaderStore())
        val second = provider(FakeStreamingPlaybackHeaderStore())

        assertSame(first.connectionPoolForTest(), second.connectionPoolForTest())
        assertSame(first.connectionPoolForTest(), first.rangeProbeConnectionPoolForTest())
    }

    @Test
    fun providersShareApplicationAudioCacheUntilEveryOwnerReleasesIt() {
        val first = provider(FakeStreamingPlaybackHeaderStore())
        val second = provider(FakeStreamingPlaybackHeaderStore())
        try {
            val sharedCache = first.audioCache()

            assertSame(sharedCache, second.audioCache())
            first.releaseAudioCache()
            assertSame(sharedCache, second.audioCache())
        } finally {
            first.releaseAudioCache()
            second.releaseAudioCache()
        }
    }

    @Test
    fun parsesRangeProbeContentLengthSafely() {
        assertEquals(8_388_608L, PlaybackMediaSourceProvider.contentLengthFromContentRange("bytes 524288-524288/8388608"))
        assertEquals(-1L, PlaybackMediaSourceProvider.contentLengthFromContentRange("bytes */*"))
        assertEquals(-1L, PlaybackMediaSourceProvider.contentLengthFromContentRange(null))
    }

    @Test
    fun playbackMediaItemForLocalTrackPreservesUriAndMetadataWithoutCacheKey() {
        val uri = Uri.parse("file:///storage/emulated/0/Music/local.flac")
        val track = Track(7L, "Local", "Artist", "Album", 180_000L, uri, "/storage/emulated/0/Music/local.flac")
        val metadata = MediaMetadata.Builder()
            .setTitle("Local")
            .build()

        val mediaItem = PlaybackMediaSourceProvider.playbackMediaItemForTrack(track, metadata)

        assertEquals("7", mediaItem.mediaId)
        assertEquals(uri, mediaItem.localConfiguration?.uri)
        assertNull(mediaItem.localConfiguration?.customCacheKey)
        assertEquals("Local", mediaItem.mediaMetadata.title.toString())
    }

    @Test
    fun playbackMediaItemForStreamingTrackPreservesResolvedUriAndStreamingCacheKeyRule() {
        val uri = Uri.parse("https://audio.example/current.flac")
        val track = Track(42L, "Stream", "Artist", "Album", 180_000L, uri, "streaming:netease:42")

        val mediaItem = PlaybackMediaSourceProvider.playbackMediaItemForTrack(track, null)

        assertEquals("42", mediaItem.mediaId)
        assertEquals(uri, mediaItem.localConfiguration?.uri)
        assertEquals(
            "streaming:netease:42|url=https://audio.example/current.flac",
            mediaItem.localConfiguration?.customCacheKey
        )
        assertEquals(
            "streaming:netease:42|url=https://audio.example/current.flac",
            PlaybackMediaSourceProvider.mediaCacheKey("streaming:netease:42", "https://audio.example/current.flac")
        )
    }

    @Test
    fun playbackMediaItemForBilibiliM4sUsesResolvedAudioMimeType() {
        val uri = Uri.parse("https://cdn.example/audio.m4s?deadline=1999999999")
        val track = Track(
            43L,
            "Bilibili Video",
            "UP 主",
            "Bilibili",
            180_000L,
            uri,
            "streaming:bilibili:video:BV1TEST:cid:42?playbackMime=audio%2Fmp4"
        )

        val mediaItem = PlaybackMediaSourceProvider.playbackMediaItemForTrack(track, null)

        assertEquals(uri, mediaItem.localConfiguration?.uri)
        assertEquals("audio/mp4", mediaItem.localConfiguration?.mimeType)
    }

    @Test
    fun playbackMediaItemForLegacyBilibiliTrackDefaultsToAudioMp4() {
        val track = Track(
            44L,
            "Legacy Bilibili Video",
            "UP 主",
            "Bilibili",
            180_000L,
            Uri.parse("https://cdn.example/legacy-audio.m4s"),
            "streaming:bilibili:video:BV1LEGACY:cid:7"
        )

        val mediaItem = PlaybackMediaSourceProvider.playbackMediaItemForTrack(track, null)

        assertEquals("audio/mp4", mediaItem.localConfiguration?.mimeType)
    }

    @Test
    fun providerMediaItemForLocalTrackUsesCallerMetadataProvider() {
        val uri = Uri.parse("file:///storage/emulated/0/Music/local.flac")
        val track = Track(7L, "Local", "Artist", "Album", 180_000L, uri, "/storage/emulated/0/Music/local.flac")
        var metadataTrack: Track? = null

        val mediaItem = provider(FakeStreamingPlaybackHeaderStore()).mediaItemForTrack(track) {
            metadataTrack = it
            MediaMetadata.Builder().setTitle(it.title).build()
        }

        assertSame(track, metadataTrack)
        assertEquals("7", mediaItem.mediaId)
        assertEquals(uri, mediaItem.localConfiguration?.uri)
        assertNull(mediaItem.localConfiguration?.customCacheKey)
        assertEquals("Local", mediaItem.mediaMetadata.title.toString())
    }

    @Test
    fun providerMediaItemForStreamingTrackUsesOwnedCacheKeyRule() {
        val uri = Uri.parse("https://audio.example/current.flac")
        val track = Track(42L, "Stream", "Artist", "Album", 180_000L, uri, "streaming:netease:42")
        var metadataTrack: Track? = null

        val mediaItem = provider(FakeStreamingPlaybackHeaderStore()).mediaItemForTrack(track) {
            metadataTrack = it
            MediaMetadata.Builder().setTitle(it.title).build()
        }

        assertSame(track, metadataTrack)
        assertEquals("42", mediaItem.mediaId)
        assertEquals(uri, mediaItem.localConfiguration?.uri)
        assertEquals(
            "streaming:netease:42|url=https://audio.example/current.flac",
            mediaItem.localConfiguration?.customCacheKey
        )
        assertEquals("Stream", mediaItem.mediaMetadata.title.toString())
    }

    @Test
    fun providerMediaCacheKeyForTrackUsesOwnedStreamingWebDavAndLocalRules() {
        val provider = provider(FakeStreamingPlaybackHeaderStore())
        val streaming = Track(
            42L,
            "Stream",
            "Artist",
            "Album",
            180_000L,
            Uri.parse("https://audio.example/current.flac"),
            "streaming:netease:42"
        )
        val local = Track(
            7L,
            "Local",
            "Artist",
            "Album",
            180_000L,
            Uri.parse("file:///storage/emulated/0/Music/local.flac"),
            "/storage/emulated/0/Music/local.flac"
        )
        val webDav = Track(
            9L,
            "WebDav",
            "Artist",
            "Album",
            180_000L,
            Uri.parse("https://dav.example/music/webdav.flac"),
            "webdav:3:/music/webdav.flac"
        )
        val localMissingUri = Track(
            8L,
            "Missing",
            "Artist",
            "Album",
            180_000L,
            Uri.EMPTY,
            "/storage/emulated/0/Music/missing.flac"
        )

        assertEquals(
            "streaming:netease:42|url=https://audio.example/current.flac",
            provider.mediaCacheKeyForTrack(streaming)
        )
        assertEquals(
            "webdav:3:/music/webdav.flac|url=https://dav.example/music/webdav.flac",
            provider.mediaCacheKeyForTrack(webDav)
        )
        assertEquals("file:///storage/emulated/0/Music/local.flac", provider.mediaCacheKeyForTrack(local))
        assertEquals("", provider.mediaCacheKeyForTrack(localMissingUri))
        assertNull(PlaybackMediaSourceProvider.mediaCacheKey("/storage/emulated/0/Music/local.flac", local.contentUri.toString()))
        assertNull(PlaybackMediaSourceProvider.mediaCacheKey(null, local.contentUri.toString()))
    }

    @Test
    fun providerReportsMissingCacheAsZeroBytesAndUnknownLength() {
        val provider = provider(FakeStreamingPlaybackHeaderStore())
        try {
            val cacheKey = "streaming:netease:missing|url=https://audio.example/missing.flac"

            assertEquals(0L, provider.continuousCachedBytes(cacheKey))
            assertEquals(-1L, provider.contentLengthForCacheKey(cacheKey))
        } finally {
            provider.releaseAudioCache()
        }
    }

    @Test
    fun providerReportsCommittedCacheSpanAsContinuousCachedBytes() {
        val provider = provider(FakeStreamingPlaybackHeaderStore())
        try {
            val cacheKey = "streaming:netease:cached|url=https://audio.example/cached.flac"
            val cacheWriter = CacheWriter(
                CacheDataSource(provider.audioCache(), ByteArrayDataSource(byteArrayOf(1, 2, 3, 4))),
                DataSpec.Builder()
                    .setUri(Uri.parse("https://audio.example/cached.flac"))
                    .setKey(cacheKey)
                    .setLength(4L)
                    .build(),
                ByteArray(1024),
                null
            )

            cacheWriter.cache()

            assertEquals(4L, provider.continuousCachedBytes(cacheKey))
            assertEquals(-1L, provider.contentLengthForCacheKey(cacheKey))
        } finally {
            provider.releaseAudioCache()
        }
    }

    @Test
    fun cachedPrefixCopyReadsOnlyCommittedCacheAndHonorsBounds() {
        val provider = provider(FakeStreamingPlaybackHeaderStore())
        val bytes = ByteArray(4096) { index -> (index % 251).toByte() }
        val track = Track(
            91L,
            "Cached WebDAV",
            "Artist",
            "Album",
            180_000L,
            Uri.parse("https://dav.example/music/cached.flac"),
            "webdav:3:/music/cached.flac"
        )
        val target = File(RuntimeEnvironment.getApplication().cacheDir, "cached-prefix-test.bin")
        try {
            val cacheKey = provider.cacheKeyForTrack(track)!!
            CacheWriter(
                CacheDataSource(provider.audioCache(), ByteArrayDataSource(bytes)),
                DataSpec.Builder()
                    .setUri(track.contentUri)
                    .setKey(cacheKey)
                    .setLength(bytes.size.toLong())
                    .build(),
                ByteArray(1024),
                null
            ).cache()

            assertEquals(2048L, provider.copyCachedPrefix(track, target, 1024L, 2048L))
            assertTrue(target.readBytes().contentEquals(bytes.copyOf(2048)))
            target.delete()
            assertEquals(0L, provider.copyCachedPrefix(track, target, 8192L, 16384L))
            assertFalse(target.exists())
        } finally {
            target.delete()
            provider.releaseAudioCache()
        }
    }

    @Test
    fun streamingCacheKeyIncludesResolvedUrl() {
        assertNotEquals(
            PlaybackMediaSourceProvider.mediaCacheKey("streaming:netease:123", "https://m10.music.126.net/a.flac"),
            PlaybackMediaSourceProvider.mediaCacheKey("streaming:netease:123", "https://m10.music.126.net/b.flac")
        )
    }

    @Test
    fun mirroredQueueReuseRequiresResolvedUriToMatch() {
        assertFalse(
            PlaybackMediaSourceProvider.mediaItemIdentityMatchesForReuse(
                "42",
                "https://audio.example/first.flac",
                "streaming:netease:42|url=https://audio.example/first.flac",
                42L,
                "https://audio.example/second.flac",
                "streaming:netease:42|url=https://audio.example/second.flac"
            )
        )
    }

    @Test
    fun mirroredQueueReuseAllowsSameResolvedMediaItem() {
        assertTrue(
            PlaybackMediaSourceProvider.mediaItemIdentityMatchesForReuse(
                "42",
                "https://audio.example/current.flac",
                "streaming:netease:42|url=https://audio.example/current.flac",
                42L,
                "https://audio.example/current.flac",
                "streaming:netease:42|url=https://audio.example/current.flac"
            )
        )
    }

    @Test
    fun providerMatchesMediaItemForTrackUsingOwnedCacheKeyRule() {
        val uri = Uri.parse("https://audio.example/current.flac")
        val track = Track(42L, "Stream", "Artist", "Album", 180_000L, uri, "streaming:netease:42")
        val mediaItem = PlaybackMediaSourceProvider.playbackMediaItemForTrack(track, null)

        assertTrue(provider(FakeStreamingPlaybackHeaderStore()).mediaItemMatchesTrackForReuse(mediaItem, track))
    }

    @Test
    fun providerMatchesTracksForReuseUsingResolvedUriRule() {
        val uri = Uri.parse("https://audio.example/current.flac")
        val current = Track(1L, "Current", "Artist", "Album", 180_000L, uri, "streaming:netease:1")
        val candidate = Track(2L, "Candidate", "Artist", "Album", 180_000L, uri, "streaming:netease:2")
        val different = Track(
            3L,
            "Different",
            "Artist",
            "Album",
            180_000L,
            Uri.parse("https://audio.example/different.flac"),
            "streaming:netease:3"
        )
        val provider = provider(FakeStreamingPlaybackHeaderStore())

        assertTrue(provider.tracksShareResolvedUriForReuse(current, candidate))
        assertFalse(provider.tracksShareResolvedUriForReuse(current, different))
        assertFalse(provider.tracksShareResolvedUriForReuse(null, candidate))
    }

    @Test
    fun providerOwnsStreamingQualityForDiagnostics() {
        val provider = provider(FakeStreamingPlaybackHeaderStore())
        val track = Track(
            42L,
            "Stream",
            "Artist",
            "Album",
            180_000L,
            Uri.parse("https://audio.example/current.flac"),
            "streaming:netease:42?quality=LOSSLESS&sourceOptions=[]"
        )

        assertEquals("lossless", provider.streamingQualityForTrack(track))
        assertEquals("", provider.streamingQualityForTrack(null))
    }

    @Test
    fun providerMatchesTrackMediaIdentityUsingIdAndResolvedUri() {
        val uri = Uri.parse("https://audio.example/current.flac")
        val current = Track(1L, "Current", "Artist", "Album", 180_000L, uri, "streaming:netease:1")
        val sameIdentity = Track(1L, "Candidate", "Artist", "Album", 180_000L, uri, "streaming:netease:1")
        val sameUriDifferentId = Track(2L, "Candidate", "Artist", "Album", 180_000L, uri, "streaming:netease:2")
        val sameIdDifferentUri = Track(
            1L,
            "Different",
            "Artist",
            "Album",
            180_000L,
            Uri.parse("https://audio.example/different.flac"),
            "streaming:netease:1"
        )
        val provider = provider(FakeStreamingPlaybackHeaderStore())

        assertTrue(provider.tracksShareMediaIdentityForReuse(current, sameIdentity))
        assertFalse(provider.tracksShareMediaIdentityForReuse(current, sameUriDifferentId))
        assertFalse(provider.tracksShareMediaIdentityForReuse(current, sameIdDifferentUri))
    }

    @Test
    fun providerBuildsLocalMediaSourceWithCallerMetadataWithoutRestoringStreamingHeaders() {
        val track = Track(
            7L,
            "Local",
            "Artist",
            "Album",
            180_000L,
            Uri.parse("file:///storage/emulated/0/Music/local.flac"),
            "/storage/emulated/0/Music/local.flac"
        )
        var metadataTrack: Track? = null
        val headerStore = FakeStreamingPlaybackHeaderStore()

        val mediaSource = provider(headerStore).mediaSourceForTrack(track) {
            metadataTrack = it
            MediaMetadata.Builder().setTitle(it.title).build()
        }

        assertSame(track, metadataTrack)
        assertNull(headerStore.restoredDataPath)
        assertEquals(PlaybackMediaSourceProvider.playbackMediaItemForTrack(track, MediaMetadata.Builder().setTitle("Local").build()), mediaSource.mediaItem)
    }

    @Test
    fun providerBuildsStreamingMediaSourceAfterRestoringPlaybackHeaders() {
        val track = Track(
            42L,
            "Stream",
            "Artist",
            "Album",
            180_000L,
            Uri.parse("https://audio.example/current.flac"),
            "streaming:netease:42"
        )
        val headerStore = FakeStreamingPlaybackHeaderStore()

        val mediaSource = provider(headerStore).mediaSourceForTrack(track) {
            MediaMetadata.Builder().setTitle(it.title).build()
        }

        assertEquals("streaming:netease:42", headerStore.restoredDataPath)
        assertEquals("42", mediaSource.mediaItem.mediaId)
        assertEquals(
            "streaming:netease:42|url=https://audio.example/current.flac",
            mediaSource.mediaItem.localConfiguration?.customCacheKey
        )
    }

    @Test
    fun providerBuildsMirroredQueueMediaSourcesWithCallerMetadata() {
        val first = Track(
            7L,
            "First",
            "Artist",
            "Album",
            180_000L,
            Uri.parse("file:///storage/emulated/0/Music/first.flac"),
            "/storage/emulated/0/Music/first.flac"
        )
        val second = Track(
            8L,
            "Second",
            "Artist",
            "Album",
            181_000L,
            Uri.parse("file:///storage/emulated/0/Music/second.flac"),
            "/storage/emulated/0/Music/second.flac"
        )
        val metadataTracks = mutableListOf<Track>()

        val mediaSources = provider(FakeStreamingPlaybackHeaderStore()).mediaSourcesForTracks(
            listOf(first, second)
        ) {
            metadataTracks.add(it)
            MediaMetadata.Builder().setTitle(it.title).build()
        }

        assertEquals(listOf(first, second), metadataTracks)
        assertEquals("7", mediaSources[0].mediaItem.mediaId)
        assertEquals("8", mediaSources[1].mediaItem.mediaId)
        assertEquals("First", mediaSources[0].mediaItem.mediaMetadata.title.toString())
        assertEquals("Second", mediaSources[1].mediaItem.mediaMetadata.title.toString())
    }

    @Test
    fun providerClassifiesHttpTracksByResolvedUri() {
        val provider = provider(FakeStreamingPlaybackHeaderStore())

        assertTrue(provider.isHttpTrack(Track(1L, "Http", "Artist", "Album", 180_000L, Uri.parse("http://audio.example/a.mp3"), "streaming:1")))
        assertTrue(provider.isHttpTrack(Track(2L, "Https", "Artist", "Album", 180_000L, Uri.parse("https://audio.example/a.mp3"), "streaming:2")))
        assertFalse(provider.isHttpTrack(Track(3L, "Local", "Artist", "Album", 180_000L, Uri.parse("content://media/audio/3"), "local")))
        assertFalse(provider.isHttpTrack(null))
    }

    @Test
    fun resolvedTrackIsPlayable() {
        val track = Track(7L, "Local", "Artist", "Album", 180_000L, Uri.parse("file:///music/local.flac"), "local")

        assertTrue(PlaybackMediaSourceProvider.hasPlayableMediaUri(track))
        assertNull(PlaybackMediaSourceProvider.unplayableMessageForTrack(track))
    }

    @Test
    fun emptyLocalUriReturnsGenericOpenError() {
        val track = Track(7L, "Local", "Artist", "Album", 180_000L, Uri.EMPTY, "local")

        assertFalse(PlaybackMediaSourceProvider.hasPlayableMediaUri(track))
        assertEquals("Unable to open this track.", PlaybackMediaSourceProvider.unplayableMessageForTrack(track))
    }

    @Test
    fun emptyStreamingPlaceholderReturnsResolutionError() {
        val track = Track(42L, "Stream", "Artist", "Album", 180_000L, Uri.EMPTY, "streaming:netease:42")

        assertEquals(
            "Streaming track is not resolved yet. Tap the track again to play.",
            PlaybackMediaSourceProvider.unplayableMessageForTrack(track)
        )
    }

    @Test
    fun restorableQueueTrackPolicyIsOwnedByMediaSourceProvider() {
        val existingFile = File.createTempFile("yukine-restorable", ".flac")
        existingFile.deleteOnExit()
        val existingFileTrack = Track(
            1L,
            "Existing",
            "Artist",
            "Album",
            180_000L,
            Uri.fromFile(existingFile),
            existingFile.absolutePath
        )
        val missingFileTrack = Track(
            2L,
            "Missing",
            "Artist",
            "Album",
            180_000L,
            Uri.fromFile(File(existingFile.parentFile, "missing-${existingFile.name}")),
            "/missing/file.flac"
        )
        val contentTrack = Track(3L, "Content", "Artist", "Album", 180_000L, Uri.parse("content://media/audio/3"), "/music/3")
        val relativeUriTrack = Track(4L, "Relative", "Artist", "Album", 180_000L, Uri.parse("relative/path.flac"), "/music/4")
        val streamingPlaceholder = Track(5L, "Stream", "Artist", "Album", 180_000L, Uri.EMPTY, "streaming:netease:5")
        val emptyLocalUri = Track(6L, "Local", "Artist", "Album", 180_000L, Uri.EMPTY, "/music/6")
        val missingDataPath = Track(7L, "Missing Data", "Artist", "Album", 180_000L, Uri.parse("content://media/audio/7"), "")
        val invalidId = Track(-1L, "Invalid", "Artist", "Album", 180_000L, Uri.parse("content://media/audio/8"), "/music/8")
        val importedDocument = Track(-42L, "Imported", "Artist", "Album", 180_000L, Uri.parse("content://documents/audio/42"), "document:content://documents/audio/42")

        assertTrue(PlaybackMediaSourceProvider.isRestorableQueueTrack(existingFileTrack))
        assertFalse(PlaybackMediaSourceProvider.isRestorableQueueTrack(missingFileTrack))
        assertTrue(PlaybackMediaSourceProvider.isRestorableQueueTrack(contentTrack))
        assertTrue(PlaybackMediaSourceProvider.isRestorableQueueTrack(relativeUriTrack))
        assertTrue(PlaybackMediaSourceProvider.isRestorableQueueTrack(streamingPlaceholder))
        assertFalse(PlaybackMediaSourceProvider.isRestorableQueueTrack(emptyLocalUri))
        assertFalse(PlaybackMediaSourceProvider.isRestorableQueueTrack(missingDataPath))
        assertFalse(PlaybackMediaSourceProvider.isRestorableQueueTrack(invalidId))
        assertTrue(PlaybackMediaSourceProvider.isRestorableQueueTrack(importedDocument))
        assertFalse(PlaybackMediaSourceProvider.isRestorableQueueTrack(null))
    }

    @Test
    fun restoredTrackForPreparationDelegatesToHeaderStore() {
        val unresolved = Track(42L, "Stream", "Artist", "Album", 180_000L, Uri.EMPTY, "streaming:netease:42")
        val resolved = Track(
            42L,
            "Stream",
            "Artist",
            "Album",
            180_000L,
            Uri.parse("https://audio.example/current.flac"),
            "streaming:netease:42"
        )
        val headerStore = FakeStreamingPlaybackHeaderStore(restoredTrack = resolved)

        val result = provider(headerStore).restoredTrackForPreparation(unresolved)

        assertSame(unresolved, headerStore.restoredTrackInput)
        assertSame(resolved, result)
    }

    @Test
    fun streamingRestoreProviderPortDelegatesToHeaderStore() {
        val unresolved = Track(42L, "Stream", "Artist", "Album", 180_000L, Uri.EMPTY, "streaming:netease:42")
        val resolved = Track(
            42L,
            "Stream",
            "Artist",
            "Album",
            180_000L,
            Uri.parse("https://audio.example/current.flac"),
            "streaming:netease:42"
        )
        val headerStore = FakeStreamingPlaybackHeaderStore(restoredTrack = resolved)
        val restoreProvider: PlaybackQueueManager.StreamingRestoreProvider = provider(headerStore)

        assertSame(resolved, restoreProvider.restoredTrackFor(unresolved))
        restoreProvider.restoreForDataPath("streaming:netease:42")

        assertSame(unresolved, headerStore.restoredTrackInput)
        assertEquals("streaming:netease:42", headerStore.restoredDataPath)
    }

    @Test
    fun prepareTrackForPlaybackUsesRestoredTrackAndOwnedUnplayablePolicy() {
        val unresolved = Track(42L, "Stream", "Artist", "Album", 180_000L, Uri.EMPTY, "streaming:netease:42")
        val resolved = Track(
            42L,
            "Stream",
            "Artist",
            "Album",
            180_000L,
            Uri.parse("https://audio.example/current.flac"),
            "streaming:netease:42"
        )
        val headerStore = FakeStreamingPlaybackHeaderStore(restoredTrack = resolved)

        val preparation = provider(headerStore).prepareTrackForPlayback(unresolved)

        assertSame(unresolved, headerStore.restoredTrackInput)
        assertSame(resolved, preparation.restoredTrack)
        assertSame(resolved, preparation.track)
        assertTrue(preparation.playable)
        assertNull(preparation.unplayableMessage)
    }

    @Test
    fun prepareTrackForPlaybackReturnsUnplayableMessageForUnresolvedTrack() {
        val unresolved = Track(42L, "Stream", "Artist", "Album", 180_000L, Uri.EMPTY, "streaming:netease:42")

        val preparation = provider(FakeStreamingPlaybackHeaderStore()).prepareTrackForPlayback(unresolved)

        assertNull(preparation.restoredTrack)
        assertSame(unresolved, preparation.track)
        assertFalse(preparation.playable)
        assertEquals(
            "Streaming track is not resolved yet. Tap the track again to play.",
            preparation.unplayableMessage
        )
    }

    @Test
    fun restoreHeadersForTrackDelegatesDataPathToHeaderStore() {
        val track = Track(42L, "Stream", "Artist", "Album", 180_000L, Uri.EMPTY, "streaming:netease:42")
        val headerStore = FakeStreamingPlaybackHeaderStore()

        assertTrue(provider(headerStore).restoreHeadersForTrack(track))

        assertEquals("streaming:netease:42", headerStore.restoredDataPath)
    }

    @Test
    fun restoreHeadersForDataPathDelegatesToHeaderStore() {
        val headerStore = FakeStreamingPlaybackHeaderStore()

        assertTrue(provider(headerStore).restoreHeadersForDataPath("streaming:netease:99"))

        assertEquals("streaming:netease:99", headerStore.restoredDataPath)
    }

    private fun provider(headerStore: StreamingPlaybackHeaderStore): PlaybackMediaSourceProvider {
        val context = RuntimeEnvironment.getApplication()
        return PlaybackMediaSourceProvider(
            context,
            MusicLibraryRepository(context, FakeStreamingDataPathParser),
            headerStore
        )
    }

    private object FakeStreamingDataPathParser : StreamingDataPathParser {
        override fun isStreamingTrack(dataPath: String): Boolean = dataPath.startsWith("streaming:")
        override fun providerName(dataPath: String): String? = dataPath.substringAfter("streaming:", "").substringBefore(":")
        override fun providerTrackId(dataPath: String): String = dataPath.substringAfterLast(":")
    }

    private class FakeStreamingPlaybackHeaderStore(
        private val restoredTrack: Track? = null
    ) : StreamingPlaybackHeaderStore {
        var restoredTrackInput: Track? = null
            private set
        var restoredDataPath: String? = null
            private set

        override fun register(dataPath: String, headers: Map<String, String>) = Unit

        override fun forDataPath(dataPath: String?): Map<String, String> = emptyMap()

        override fun restoreForDataPath(dataPath: String?): Boolean {
            restoredDataPath = dataPath
            return true
        }

        override fun restoredTrackFor(track: Track?): Track? {
            restoredTrackInput = track
            return restoredTrack
        }
    }
}
