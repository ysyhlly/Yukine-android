package app.yukine

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class StatusMessageState(
    val message: String = ""
)

internal class StatusMessageViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(StatusMessageState())
    val state: StateFlow<StatusMessageState> = mutableState.asStateFlow()

    fun applyStatus(status: String?, languageMode: String): String {
        val message = localize(status, languageMode)
        mutableState.value = StatusMessageState(message)
        return message
    }

    companion object {
        @JvmStatic
        fun localize(status: String?, languageMode: String): String {
            if (status == null || status.trim().isEmpty()) {
                return ""
            }
            val value = status.trim()
            return when {
                value == "Status" -> AppLanguage.text(languageMode, "status")
                value == "Loading library" -> AppLanguage.text(languageMode, "loading.library")
                value == "Audio permission required" -> AppLanguage.text(languageMode, "audio.permission.required")
                value == "No tracks to play" -> AppLanguage.text(languageMode, "no.tracks.to.play")
                value == "Queue is not connected" -> AppLanguage.text(languageMode, "queue.not.connected")
                value == "Playback service is not connected" -> AppLanguage.text(languageMode, "playback.service.not.connected")
                value == "Cookie is empty" || value == "Cookie 为空" -> AppLanguage.text(languageMode, "streaming.cookie.empty")
                value == "Cookie saved" || value == "Cookie 已保存" -> AppLanguage.text(languageMode, "streaming.cookie.saved")
                value == "Choose a streaming provider to sign in" -> AppLanguage.text(languageMode, "streaming.choose.login.provider")
                value.startsWith("Status: ") -> AppLanguage.text(languageMode, "status") + ": " + value.substring("Status: ".length)
                else -> PlaybackErrorMessageLocalizer.localize(value, languageMode)
            }
        }
    }
}
