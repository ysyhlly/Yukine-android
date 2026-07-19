package app.yukine

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import app.yukine.playback.IdentityEnhancementPlaybackGate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Android entry point: lifecycle, Compose root, playback service and platform launcher delegation. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    internal lateinit var composition: MainActivityComposition

    private lateinit var features: MainActivityFeatureBindings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IdentityEnhancementPlaybackGate.setAppVisible(true)
        features = composition.create(this)
        features.settings.initialize {
            features.onboarding.initialize {
                if (isFinishing || isDestroyed) return@initialize
                features.navigation.bindRoot(
                    features.viewModels,
                    features.onboarding.owner(),
                    features.platform.permissionController(),
                    features.playback.nowPlayingEffectOwner,
                    features.library.playlistDialogController(),
                    features.playback.queueActionController,
                    features.platform.documentPickerController(),
                    features.platform.trackDownloadManager(),
                    features.playback.connection
                )
                features.playback.bindService()
                features.playback.setAppVisible(
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                )
                features.onboarding.startLibrary()
                features.streaming.handleInitialIntent(intent)
                handleFloatingLyricsIntent(intent)
                features.platform.applyThemeSurface()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        IdentityEnhancementPlaybackGate.setAppVisible(true)
        if (::features.isInitialized) {
            features.playback.setAppVisible(true)
            features.streaming.onResume()
            features.settings.onResume()
        }
    }

    override fun onPause() {
        if (::features.isInitialized) {
            features.streaming.onPause()
            features.playback.setAppVisible(false)
        }
        super.onPause()
    }

    override fun onStop() {
        IdentityEnhancementPlaybackGate.setAppVisible(false)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::features.isInitialized) {
            features.streaming.handleNewIntent(intent)
            handleFloatingLyricsIntent(intent)
        }
    }

    override fun onDestroy() {
        if (::features.isInitialized) {
            features.settings.release()
            features.navigation.release()
            features.onboarding.release()
            features.network.release()
            features.library.release()
            features.playback.release()
            features.streaming.release()
            features.platform.shutdown()
        }
        super.onDestroy()
    }

    private fun handleFloatingLyricsIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(FloatingLyricsService.EXTRA_OPEN_SETTINGS, false) != true) {
            return
        }
        intent.removeExtra(FloatingLyricsService.EXTRA_OPEN_SETTINGS)
        features.navigation.openFloatingLyricsSettings()
    }
}
