package app.yukine

import app.yukine.navigation.HomeTab
import app.yukine.navigation.LibraryTab
import app.yukine.navigation.QueueTab
import app.yukine.navigation.SettingsTab
import app.yukine.navigation.TabRoute
import app.yukine.ui.OnboardingActions
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MainNavHostMountTest {
    @Test
    fun exposesTypedTabsAndInjectedShellActionsWithoutActivityPolicy() {
        var language = AppLanguage.MODE_ENGLISH
        val calls = mutableListOf<String>()
        val onboarding = MutableStateFlow(OnboardingUiState())
        val onboardingActions = OnboardingActions(
            Runnable { calls += "permissions" },
            Runnable { calls += "folder" },
            Runnable { calls += "scan" },
            Runnable { calls += "import" },
            Runnable { calls += "streaming" },
            Runnable { calls += "finish" }
        )
        val mount = MainNavHostMount(
            hostStateProvider = { error("host state is not needed by this contract test") },
            onboarding = onboarding,
            languageModeProvider = { language },
            onboardingActionSet = onboardingActions,
            closeNowPlaying = Runnable { calls += "close" },
            nowPlayingEvents = { calls += "event:${it::class.simpleName}" },
            tabChanges = { calls += "tab:${it.route}" }
        )

        assertEquals(
            listOf<TabRoute>(HomeTab, LibraryTab, QueueTab, SettingsTab),
            mount.tabs().map { it.tab }
        )
        assertEquals(listOf("Home", "Library", "Playing", "Settings"), mount.tabs().map { it.label })
        assertSame(onboarding, mount.onboardingState())
        assertSame(onboardingActions, mount.onboardingActions())

        mount.closeNowPlayingAction().run()
        mount.nowPlayingEventHandler()(NowPlayingEvent.OpenQueue)
        mount.onTabChanged(LibraryTab)
        mount.onboardingActions().requestPermissions.run()
        assertEquals(listOf("close", "event:OpenQueue", "tab:library", "permissions"), calls)

        language = AppLanguage.MODE_CHINESE
        assertEquals("主页", mount.tabs().first().label)
    }
}
