package app.yukine

import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.lifecycle.LifecycleOwner

internal class BackNavigationBindings(
    private val handleBackAction: () -> Boolean
) {
    fun install(owner: LifecycleOwner, dispatcher: OnBackPressedDispatcher): OnBackPressedCallback {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (handleBackAction()) {
                    return
                }
                isEnabled = false
                dispatcher.onBackPressed()
            }
        }
        dispatcher.addCallback(owner, callback)
        return callback
    }
}
