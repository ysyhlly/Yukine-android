package app.yukine

import android.os.Handler
import app.yukine.data.MusicLibraryRepository
import app.yukine.model.Track

internal fun interface CanonicalPlaybackSourceCallback {
    fun onResolved(track: Track)
}

internal fun interface CanonicalPlaybackSourceResolver {
    /** Returns true only when an asynchronous canonical lookup was scheduled. */
    fun resolve(track: Track, callback: CanonicalPlaybackSourceCallback): Boolean
}

/** Keeps Room work off the UI thread and always returns a playable fallback candidate. */
internal class AsyncCanonicalPlaybackSourceResolver(
    private val repository: MusicLibraryRepository,
    private val executors: MainExecutors,
    private val mainHandler: Handler
) : CanonicalPlaybackSourceResolver {
    override fun resolve(track: Track, callback: CanonicalPlaybackSourceCallback): Boolean {
        executors.io {
            val resolved = runCatching { repository.loadActivePlaybackSource(track) }
                .getOrNull()
                ?: track
            mainHandler.post { callback.onResolved(resolved) }
        }
        return true
    }
}
