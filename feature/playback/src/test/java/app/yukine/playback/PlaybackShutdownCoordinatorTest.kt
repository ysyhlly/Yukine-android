package app.yukine.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackShutdownCoordinatorTest {
    @Test
    fun releasePlaybackResourcesKeepsServiceResourcesAlive() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(calls)

        coordinator.releasePlaybackResources()

        assertEquals(listOf("lyrics", "wifi", "player"), calls)
    }

    @Test
    fun releasePlaybackResourcesRunsOnce() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(calls)

        coordinator.releasePlaybackResources()
        coordinator.releasePlaybackResources()

        assertEquals(listOf("lyrics", "wifi", "player"), calls)
    }

    @Test
    fun handleServiceDestroyedRunsFullServiceTeardown() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(calls)

        coordinator.handleServiceDestroyed()

        assertEquals(
            listOf("position", "lyrics", "noisy", "warmup", "analyzer", "recovery-scheduler", "schedulers", "recovery", "progress", "sleep", "crossfade", "callbacks", "visualization", "artwork", "precache", "state", "wifi", "player"),
            calls
        )
    }

    @Test
    fun handleTaskRemovedPersistsResumeRequestFromPlaybackState() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(calls, playing = false, preparing = true, notificationWorthy = false)

        coordinator.handleTaskRemoved()

        assertEquals(listOf("position", "queue", "resume:true"), calls)
    }

    @Test
    fun handleTaskRemovedPublishesNotificationOnlyWhenWorthy() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(calls, playing = true, preparing = false, notificationWorthy = true)

        coordinator.handleTaskRemoved()

        assertEquals(listOf("position", "queue", "resume:true", "notification"), calls)
    }

    @Test
    fun handleServiceDestroyedPersistsPositionBeforeFullServiceTeardown() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(calls)

        coordinator.handleServiceDestroyed()

        assertEquals(
            listOf("position", "lyrics", "noisy", "warmup", "analyzer", "recovery-scheduler", "schedulers", "recovery", "progress", "sleep", "crossfade", "callbacks", "visualization", "artwork", "precache", "state", "wifi", "player"),
            calls
        )
    }

    @Test
    fun handleServiceDestroyedRunsTeardownOnce() {
        val calls = mutableListOf<String>()
        val coordinator = coordinator(calls)

        coordinator.handleServiceDestroyed()
        coordinator.handleServiceDestroyed()

        assertEquals(
            listOf("position", "lyrics", "noisy", "warmup", "analyzer", "recovery-scheduler", "schedulers", "recovery", "progress", "sleep", "crossfade", "callbacks", "visualization", "artwork", "precache", "state", "wifi", "player"),
            calls
        )
    }

    private fun coordinator(
        calls: MutableList<String>,
        playing: Boolean = false,
        preparing: Boolean = false,
        notificationWorthy: Boolean = false
    ): PlaybackShutdownCoordinator {
        return PlaybackShutdownCoordinator(
            playbackResources = object : PlaybackShutdownCoordinator.PlaybackResources {
                override fun releaseLyrics() {
                    calls.add("lyrics")
                }

                override fun releaseWifiLock() {
                    calls.add("wifi")
                }

                override fun releasePlayer() {
                    calls.add("player")
                }
            },
            serviceResources = object : PlaybackShutdownCoordinator.ServiceResources {
                override fun unregisterNoisyReceiver() {
                    calls.add("noisy")
                }

                override fun releaseWarmup() {
                    calls.add("warmup")
                }

                override fun releaseVisualizationAnalyzer() {
                    calls.add("analyzer")
                }

                override fun releaseRecoveryScheduler() {
                    calls.add("recovery-scheduler")
                }

                override fun shutdownTaskSchedulers() {
                    calls.add("schedulers")
                }

                override fun releaseErrorRecovery() {
                    calls.add("recovery")
                }

                override fun releaseProgressUpdates() {
                    calls.add("progress")
                }

                override fun releaseSleepTimer() {
                    calls.add("sleep")
                }

                override fun releaseCrossfade() {
                    calls.add("crossfade")
                }

                override fun clearMainCallbacks() {
                    calls.add("callbacks")
                }

                override fun releaseVisualizationCache() {
                    calls.add("visualization")
                }

                override fun releaseNotificationArtwork() {
                    calls.add("artwork")
                }

                override fun releasePrecache() {
                    calls.add("precache")
                }

                override fun releaseStatePublisher() {
                    calls.add("state")
                }
            },
            lifecycleResources = object : PlaybackShutdownCoordinator.LifecycleResources {
                override fun persistPlaybackPosition() {
                    calls.add("position")
                }

                override fun persistPlaybackQueue() {
                    calls.add("queue")
                }

                override fun savePlaybackResumeRequested(requested: Boolean) {
                    calls.add("resume:$requested")
                }

                override fun isPlaying(): Boolean {
                    return playing
                }

                override fun isPreparing(): Boolean {
                    return preparing
                }

                override fun hasNotificationWorthyState(): Boolean {
                    return notificationWorthy
                }

                override fun publishPlaybackNotification() {
                    calls.add("notification")
                }
            }
        )
    }
}
