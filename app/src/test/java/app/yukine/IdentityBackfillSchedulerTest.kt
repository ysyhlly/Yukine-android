package app.yukine

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class IdentityBackfillSchedulerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .build()
        )
        IdentityBackfillCheckpointStore(context).reset()
    }

    @After
    fun tearDown() {
        WorkManager.getInstance(context).cancelAllWork().result.get()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun automaticSchedulingFromMainThreadEnqueuesWithoutReadingRoom() {
        val result = IdentityBackfillScheduler.scheduleAutomatic(context)

        assertEquals(IdentityBackfillScheduleKind.ENQUEUED, result.kind)
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(IdentityBackfillScheduler.UNIQUE_WORK_NAME)
            .get()
        assertTrue(workInfos.isNotEmpty())
        assertNotEquals(
            IdentityBackfillRuntimeState.FAILED,
            IdentityBackfillCheckpointStore(context).runtimeStatus().state
        )
    }

    @Test
    fun automaticSchedulingKeepsExistingActiveWork() {
        val manager = WorkManager.getInstance(context)
        assertEquals(
            IdentityBackfillScheduleKind.ENQUEUED,
            IdentityBackfillScheduler.scheduleAutomatic(context).kind
        )
        val first = manager
            .getWorkInfosForUniqueWork(IdentityBackfillScheduler.UNIQUE_WORK_NAME)
            .get()
            .single()

        assertEquals(
            IdentityBackfillScheduleKind.ENQUEUED,
            IdentityBackfillScheduler.scheduleAutomatic(context).kind
        )
        val afterSecondSchedule = manager
            .getWorkInfosForUniqueWork(IdentityBackfillScheduler.UNIQUE_WORK_NAME)
            .get()

        assertEquals(1, afterSecondSchedule.size)
        assertEquals(first.id, afterSecondSchedule.single().id)
    }
}
