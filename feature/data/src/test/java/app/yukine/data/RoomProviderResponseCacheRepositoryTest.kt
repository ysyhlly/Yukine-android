package app.yukine.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.yukine.data.room.YukineDatabase
import app.yukine.identity.ProviderCacheFreshness
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomProviderResponseCacheRepositoryTest {
    @get:Rule
    val backgroundDatabase = BackgroundDatabaseTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "provider-response-cache-test.db"
    private lateinit var database: YukineDatabase
    private lateinit var repository: RoomProviderResponseCacheRepository

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        database = YukineDatabase.openForTest(context, name)
        repository = RoomProviderResponseCacheRepository(
            database = database,
            failureThreshold = 3,
            circuitDurationMs = 30L * 60L * 1_000L
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun responseBecomesStaleButRemainsAvailableOffline() {
        assertNull(repository.response("musicbrainz", "official", "query", 100L))

        repository.saveSuccess("musicbrainz", "official", "query", "{\"ok\":true}", 100L, 50L)

        assertEquals(
            ProviderCacheFreshness.FRESH,
            repository.response("musicbrainz", "official", "query", 149L)?.freshness
        )
        assertEquals(
            ProviderCacheFreshness.STALE,
            repository.response("musicbrainz", "official", "query", 150L)?.freshness
        )
    }

    @Test
    fun circuitIsEndpointSpecificAndClosesAfterTimeout() {
        val official = "https://musicbrainz.org/ws/2/"
        val eu = "https://musicbrainz.eu/ws/2/"

        repository.recordFailure("musicbrainz", official, "timeout-1", 1_000L)
        repository.recordFailure("musicbrainz", official, "timeout-2", 2_000L)
        val opened = repository.recordFailure("musicbrainz", official, "timeout-3", 3_000L)

        assertFalse(opened.canRequest(3_000L))
        assertTrue(repository.endpointHealth("musicbrainz", eu).canRequest(3_000L))
        assertTrue(opened.canRequest(3_000L + 30L * 60L * 1_000L))
    }

    @Test
    fun successfulRequestResetsEndpointFailureState() {
        val endpoint = "https://musicbrainz.org/ws/2/"
        repository.recordFailure("musicbrainz", endpoint, "timeout", 100L)

        repository.saveSuccess("musicbrainz", endpoint, "query", "{}", 200L, 1_000L)

        val health = repository.endpointHealth("musicbrainz", endpoint)
        assertEquals(0, health.failureCount)
        assertEquals(0L, health.circuitOpenUntil)
        assertEquals("", health.lastError)
    }

    @Test
    fun cachedResponseAndCircuitStateSurviveRepositoryRecreation() {
        val endpoint = "https://musicbrainz.org/ws/2/"
        repository.saveSuccess("musicbrainz", endpoint, "query", "{\"ok\":true}", 100L, 1_000L)
        repeat(3) { index ->
            repository.recordFailure("musicbrainz", endpoint, "failure-$index", 200L + index)
        }

        val recreated = RoomProviderResponseCacheRepository(database)

        assertEquals("{\"ok\":true}", recreated.response("musicbrainz", endpoint, "query", 300L)?.responseJson)
        assertFalse(recreated.endpointHealth("musicbrainz", endpoint).canRequest(300L))
    }
}
