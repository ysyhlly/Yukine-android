package app.yukine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val routeState = MutableStateFlow(MainActivityRouteStateStore.restore(savedStateHandle))
    val state: StateFlow<MainActivityRouteState> = routeState.asStateFlow()

    fun updateRoute(snapshot: MainActivityRouteState) {
        routeState.value = snapshot
        MainActivityRouteStateStore.save(savedStateHandle, snapshot)
    }
}
