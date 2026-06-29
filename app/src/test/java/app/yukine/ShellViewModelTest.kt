package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellViewModelTest {
    @Test
    fun selectTabUpdatesTabAndContentRoute() {
        val viewModel = ShellViewModel()

        viewModel.onAction(ShellAction.SelectTab(MainRoutes.TAB_LIBRARY))

        assertEquals(MainRoutes.TAB_LIBRARY, viewModel.state.value.selectedTab)
        assertEquals(MainRoutes.TAB_LIBRARY, viewModel.state.value.contentRoute)
    }

    @Test
    fun navigateContentKeepsSelectedTab() {
        val viewModel = ShellViewModel()
        viewModel.onAction(ShellAction.SelectTab(MainRoutes.TAB_NETWORK))

        viewModel.onAction(ShellAction.NavigateContent(MainRoutes.NETWORK_SOURCES))

        assertEquals(MainRoutes.TAB_NETWORK, viewModel.state.value.selectedTab)
        assertEquals(MainRoutes.NETWORK_SOURCES, viewModel.state.value.contentRoute)
    }

    @Test
    fun showStatusAndScrollRequestUpdateShellState() {
        val viewModel = ShellViewModel()

        viewModel.onAction(ShellAction.ShowStatus("Ready"))
        viewModel.onAction(ShellAction.RequestScrollToTop)
        viewModel.onAction(ShellAction.RequestScrollToTop)

        assertEquals("Ready", viewModel.state.value.statusMessage)
        assertEquals(2L, viewModel.state.value.scrollToTopRequest)
    }
}
