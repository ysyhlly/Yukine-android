package app.yukine

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLifecycleInstrumentedTest {
    @Test
    fun activitySurvivesBackgroundResumeAndRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                assertTrue(!activity.isFinishing)
                assertTrue(!activity.isDestroyed)
            }
        }
    }
}
