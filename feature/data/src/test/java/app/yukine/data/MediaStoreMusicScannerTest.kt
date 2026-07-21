package app.yukine.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaStoreMusicScannerTest {

    @Test
    fun filtersCallRecordingsByPath() {
        assertTrue(MediaStoreMusicScanner.isCallRecording(null, null, null, "/storage/emulated/0/CallRecordings/call_001.mp3"))
        assertTrue(MediaStoreMusicScanner.isCallRecording(null, null, null, "/music/callrecord/song.mp3"))
        assertTrue(MediaStoreMusicScanner.isCallRecording(null, null, null, "/music/call_record/song.mp3"))
        assertTrue(MediaStoreMusicScanner.isCallRecording(null, null, null, "/record/call/song.mp3"))
        assertTrue(MediaStoreMusicScanner.isCallRecording(null, null, null, "/recordings/call/song.mp3"))
    }

    @Test
    fun filtersCallRecordingsByMetadata() {
        assertTrue(MediaStoreMusicScanner.isCallRecording("Call Recordings", null, null, null))
        assertTrue(MediaStoreMusicScanner.isCallRecording("通话录音", null, null, null))
        assertTrue(MediaStoreMusicScanner.isCallRecording(null, null, "Call Recordings", null))
    }

    @Test
    fun doesNotFilterNormalMusic() {
        assertFalse(MediaStoreMusicScanner.isCallRecording("Song", "Artist", "Album", "/music/song.flac"))
        assertFalse(MediaStoreMusicScanner.isCallRecording(null, null, null, "/music/calling_you.flac"))
    }

    @Test
    fun filtersEncryptedCacheExtensions() {
        assertTrue(MediaStoreMusicScanner.isThirdPartyCache("/music/cache/song.kgm"))
        assertTrue(MediaStoreMusicScanner.isThirdPartyCache("/music/cache/song.vpr"))
        assertTrue(MediaStoreMusicScanner.isThirdPartyCache("/music/cache/song.ofl"))
        assertTrue(MediaStoreMusicScanner.isThirdPartyCache("/music/cache/song.qmc"))
        assertTrue(MediaStoreMusicScanner.isThirdPartyCache("/music/cache/song.mflac"))
        assertTrue(MediaStoreMusicScanner.isThirdPartyCache("/music/cache/song.mgg"))
        assertTrue(MediaStoreMusicScanner.isThirdPartyCache("/music/cache/song.kgc"))
        assertTrue(MediaStoreMusicScanner.isThirdPartyCache("/music/cache/song.krc"))
    }

    @Test
    fun filtersNonStandardFilesInCacheDirectories() {
        assertTrue(MediaStoreMusicScanner.isThirdPartyCache("/kugou/cache/file.tmp"))
        assertTrue(MediaStoreMusicScanner.isThirdPartyCache("/kugoumusic/cache/file.dat"))
        assertTrue(MediaStoreMusicScanner.isThirdPartyCache("/qqmusic/cache/file.bin"))
        assertTrue(MediaStoreMusicScanner.isThirdPartyCache("/tencent/cache/file.tmp"))
        assertTrue(MediaStoreMusicScanner.isThirdPartyCache("/netease/cloudmusic/cache/file.dat"))
    }

    @Test
    fun allowsDecodableFilesInCacheDirectories() {
        assertFalse(MediaStoreMusicScanner.isThirdPartyCache("/kugou/cache/song.mp3"))
        assertFalse(MediaStoreMusicScanner.isThirdPartyCache("/qqmusic/cache/song.flac"))
        assertFalse(MediaStoreMusicScanner.isThirdPartyCache("/tencent/cache/song.m4a"))
    }

    @Test
    fun allowsNormalMusicFiles() {
        assertFalse(MediaStoreMusicScanner.isThirdPartyCache("/music/song.flac"))
        assertFalse(MediaStoreMusicScanner.isThirdPartyCache("/music/song.mp3"))
        assertFalse(MediaStoreMusicScanner.isThirdPartyCache(null))
        assertFalse(MediaStoreMusicScanner.isThirdPartyCache(""))
    }
}
