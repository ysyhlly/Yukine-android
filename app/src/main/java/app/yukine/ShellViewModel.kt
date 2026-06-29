package app.yukine

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ShellState(
    val selectedTab: String = MainRoutes.TAB_HOME,
    val contentRoute: String = MainRoutes.TAB_HOME,
    val statusMessage: String = "",
    val scrollToTopRequest: Long = 0L
)

sealed interface ShellAction {
    data class SelectTab(val tab: String) : ShellAction
    data class NavigateContent(val route: String) : ShellAction
    data class ShowStatus(val message: String) : ShellAction
    data object RequestScrollToTop : ShellAction
}

sealed interface NavigationAction {
    data class SelectTab(val tab: String) : NavigationAction
    data class NavigateContent(val route: String) : NavigationAction
}

sealed interface ShellEffect {
    data class ShowStatus(val message: String) : ShellEffect
}

@HiltViewModel
class ShellViewModel @Inject constructor() : ViewModel() {
    private val shellState = MutableStateFlow(ShellState())
    val state: StateFlow<ShellState> = shellState.asStateFlow()

    fun onAction(action: ShellAction) {
        when (action) {
            is ShellAction.SelectTab -> updateNavigation(action.tab, action.tab)
            is ShellAction.NavigateContent -> updateNavigation(shellState.value.selectedTab, action.route)
            is ShellAction.ShowStatus -> shellState.value = shellState.value.copy(statusMessage = action.message)
            ShellAction.RequestScrollToTop -> shellState.value = shellState.value.copy(
                scrollToTopRequest = shellState.value.scrollToTopRequest + 1L
            )
        }
    }

    fun onNavigationAction(action: NavigationAction) {
        when (action) {
            is NavigationAction.SelectTab -> onAction(ShellAction.SelectTab(action.tab))
            is NavigationAction.NavigateContent -> onAction(ShellAction.NavigateContent(action.route))
        }
    }

    private fun updateNavigation(tab: String, route: String) {
        shellState.value = shellState.value.copy(
            selectedTab = tab.ifBlank { MainRoutes.TAB_HOME },
            contentRoute = route.ifBlank { MainRoutes.TAB_HOME }
        )
    }
}
