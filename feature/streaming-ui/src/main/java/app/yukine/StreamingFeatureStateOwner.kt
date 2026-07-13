package app.yukine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The only mutable owner of aggregate streaming presentation state. */
internal class StreamingFeatureStateOwner(
    initial: StreamingSearchState = StreamingSearchState()
) {
    private val mutableState = MutableStateFlow(initial)

    val state: StateFlow<StreamingSearchState> = mutableState.asStateFlow()

    var value: StreamingSearchState
        get() = mutableState.value
        set(value) {
            mutableState.value = value
        }
}
