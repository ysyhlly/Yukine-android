package app.yukine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import app.yukine.data.IdentityBackfillCheckpoint
import app.yukine.data.IdentityBackfillProgress
import app.yukine.data.IdentityBackfillStage
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IdentityBackfillRuntimeStatusTest {
    private lateinit var context: Context
    private lateinit var store: IdentityBackfillCheckpointStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = IdentityBackfillCheckpointStore(context)
        store.reset()
    }

    @Test
    fun runtimeStateAndWorkIdSurviveStoreRecreation() {
        store.markWork(
            workId = "work-123",
            state = IdentityBackfillRuntimeState.QUEUED
        )

        val restored = IdentityBackfillCheckpointStore(context).runtimeStatus()

        assertEquals(IdentityBackfillRuntimeState.QUEUED, restored.state)
        assertEquals("work-123", restored.workId)
        assertEquals("", restored.errorMessage)
        assertEquals(true, restored.updatedAt > 0L)
    }

    @Test
    fun failureAndResetArePersisted() {
        store.markWork("work-456", IdentityBackfillRuntimeState.RUNNING)
        store.updateRuntimeState(
            IdentityBackfillRuntimeState.FAILED,
            "database unavailable"
        )

        val failed = store.runtimeStatus()
        assertEquals(IdentityBackfillRuntimeState.FAILED, failed.state)
        assertEquals("work-456", failed.workId)
        assertEquals("database unavailable", failed.errorMessage)

        store.reset()

        assertEquals(
            IdentityBackfillRuntimeStatus(),
            IdentityBackfillCheckpointStore(context).runtimeStatus()
        )
    }

    @Test
    fun legacyCompleteCheckpointInfersCompletedState() {
        store.save(
            IdentityBackfillCheckpoint(
                stage = IdentityBackfillStage.COMPLETE,
                progress = IdentityBackfillProgress(total = 10, processed = 10)
            )
        )

        assertEquals(
            IdentityBackfillRuntimeState.COMPLETED,
            store.runtimeStatus().state
        )
    }

    @Test
    fun workManagerStatesMapToUiRuntimeStates() {
        assertEquals(
            IdentityBackfillRuntimeState.QUEUED,
            WorkInfo.State.ENQUEUED.toIdentityBackfillRuntimeState()
        )
        assertEquals(
            IdentityBackfillRuntimeState.QUEUED,
            WorkInfo.State.BLOCKED.toIdentityBackfillRuntimeState()
        )
        assertEquals(
            IdentityBackfillRuntimeState.RUNNING,
            WorkInfo.State.RUNNING.toIdentityBackfillRuntimeState()
        )
        assertEquals(
            IdentityBackfillRuntimeState.COMPLETED,
            WorkInfo.State.SUCCEEDED.toIdentityBackfillRuntimeState()
        )
        assertEquals(
            IdentityBackfillRuntimeState.FAILED,
            WorkInfo.State.FAILED.toIdentityBackfillRuntimeState()
        )
        assertEquals(
            IdentityBackfillRuntimeState.CANCELLED,
            WorkInfo.State.CANCELLED.toIdentityBackfillRuntimeState()
        )
    }
}
