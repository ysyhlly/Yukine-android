package app.echo.next.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestedRoutePolicyTest {
    @Test
    fun acceptsOnlyTheExplicitlyRequestedRoute() {
        assertTrue(shouldAcceptRequestedRoute("network", "network"))
        assertFalse(shouldAcceptRequestedRoute("library", "network"))
        assertFalse(shouldAcceptRequestedRoute("home", ""))
    }
}
