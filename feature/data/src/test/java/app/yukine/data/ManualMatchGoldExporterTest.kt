package app.yukine.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.yukine.data.room.YukineDatabase
import app.yukine.model.Track
import java.security.SecureRandom
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ManualMatchGoldExporterTest {
    @get:Rule
    val backgroundDatabase = BackgroundDatabaseTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "manual-match-gold-exporter-test.db"
    private lateinit var database: YukineDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        database = YukineDatabase.openForTest(context, databaseName)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun exportsOnlyWhitelistedMetadataWithPerExportOpaquePairIds() {
        val recordings = RoomRecordingIdentityRepository(database)
        val first = recordings.ensureCanonicalForTrack(track(901L, "同一首歌", "/private/left.flac"))
        val second = recordings.ensureCanonicalForTrack(
            track(902L, "同一首歌 专辑版", "/private/right.flac")
        )
        val decisions = ManualMatchDecisionStore(database)
        decisions.record(
            label = ManualMatchLabel.SAME,
            left = checkNotNull(decisions.representative(first.recordingId)),
            right = checkNotNull(decisions.representative(second.recordingId)),
            note = "TEST_EXPORT",
            sourceRecordingId = first.recordingId,
            targetRecordingId = second.recordingId
        )

        val firstExport = ManualMatchGoldExporter(database, SecureRandom()).exportJsonl()
        val secondExport = ManualMatchGoldExporter(database, SecureRandom()).exportJsonl()
        val json = JSONObject(firstExport)

        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals("SAME", json.getString("label"))
        assertEquals("同一首歌", json.getJSONObject("left").getString("title"))
        assertTrue(json.getString("pairId").matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(
            json.getString("pairId"),
            JSONObject(secondExport).getString("pairId")
        )
        assertFalse(firstExport.contains("/private/"))
        assertFalse(firstExport.contains("file:"))
        assertFalse(firstExport.contains("providerTrackId"))
        assertFalse(firstExport.contains("sourceId"))
        assertFalse(firstExport.contains("recordingId"))
    }

    private fun track(id: Long, title: String, path: String): Track = Track(
        id,
        title,
        "人工艺术家",
        "人工专辑",
        180_000L,
        Uri.parse("file://$path"),
        path
    )
}
