package app.yukine.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SettingsScreenTest {
    @Test
    fun contentActionsRemoveTheActualBackActionWithoutDroppingEarlierContent() {
        val first = SettingsAction("First", Runnable {})
        val back = SettingsAction("Back", Runnable {}, isBack = true)
        val last = SettingsAction("Last", Runnable {})

        assertEquals(listOf(first, last), settingsContentActions(listOf(first, back, last)))
    }

    @Test
    fun contentActionsReuseTheOriginalListWhenThereIsNoBackAction() {
        val actions = listOf(SettingsAction("Action", Runnable {}))

        assertSame(actions, settingsContentActions(actions))
    }
}
