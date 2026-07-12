package app.yukine

import app.yukine.streaming.StreamingQualityPreference
import org.junit.Assert.assertEquals
import org.junit.Test

class MainSettingsStoreTest {
    @Test
    fun preferencesSnapshotPreservesAutomaticQualityDowngradePreference() {
        val store = MainSettingsStore()
        val expected = SettingsPreferencesSnapshot(
            streamingAudioQuality = StreamingQualityPreference.LOSSLESS,
            refuseAutomaticQualityDowngrade = true
        )

        store.sync(expected)

        assertEquals(expected, store.preferencesSnapshot())
    }
}
