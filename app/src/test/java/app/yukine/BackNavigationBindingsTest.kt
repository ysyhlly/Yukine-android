package app.yukine

import androidx.activity.OnBackPressedDispatcher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackNavigationBindingsTest {
    @Test
    fun handlesBackWithProvidedLambda() {
        var calls = 0
        val bindings = BackNavigationBindings {
            calls += 1
            true
        }
        val dispatcher = OnBackPressedDispatcher()
        val owner = FakeLifecycleOwner()

        val callback = bindings.install(owner, dispatcher)
        callback.handleOnBackPressed()

        assertEquals(1, calls)
    }
}

private class FakeLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this).apply {
        currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle = registry
}
