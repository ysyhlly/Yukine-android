package app.yukine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yukine.navigation.SettingsTab
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val routeState = MutableStateFlow(NavigationRouteStateStore.restore(savedStateHandle))
    val state: StateFlow<NavigationRouteState> = routeState.asStateFlow()
    val searchQuery: StateFlow<String> = state
        .map { it.searchQuery }
        .stateIn(viewModelScope, SharingStarted.Eagerly, state.value.searchQuery)
    val settingsRouteState: StateFlow<SettingsRouteState> = state
        .map { route -> SettingsRouteState(route.selectedTab == SettingsTab, route.settingsPage) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SettingsRouteState(state.value.selectedTab == SettingsTab, state.value.settingsPage)
        )

    fun updateRoute(snapshot: NavigationRouteState) {
        routeState.value = snapshot
        NavigationRouteStateStore.save(savedStateHandle, snapshot)
    }
}
