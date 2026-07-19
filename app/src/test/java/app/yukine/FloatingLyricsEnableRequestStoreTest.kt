package app.yukine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FloatingLyricsEnableRequestStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = FloatingLyricsEnableRequestStore(context)

    @Before
    fun clear() {
        store.clear()
    }

    @Test
    fun deniedPermissionDoesNotConsumePendingEnableRequest() {
        store.markPending()

        assertFalse(store.consumeIfGranted(permissionGranted = false))
        assertTrue(store.consumeIfGranted(permissionGranted = true))
    }

    @Test
    fun grantedPermissionConsumesRequestExactlyOnce() {
        store.markPending()

        assertTrue(store.consumeIfGranted(permissionGranted = true))
        assertFalse(store.consumeIfGranted(permissionGranted = true))
    }
}
