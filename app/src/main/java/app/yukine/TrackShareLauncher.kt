package app.yukine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import app.yukine.model.Track

internal fun interface TrackShareLanguageProvider {
    fun languageMode(): String
}

internal fun interface TrackShareStyleProvider {
    fun shareStyle(): String
}

internal fun interface TrackShareActivityStarter {
    fun startActivity(intent: Intent)
}

internal interface TrackShareStatusSink {
    fun showFeedback(message: String)

    fun setStatus(message: String)
}

internal interface TrackShareOperations {
    fun isShareAvailable(): Boolean

    fun musicSharePayload(track: Track): TrackMusicSharePayload?

    fun shareNative(activity: Activity, track: Track, payload: TrackMusicSharePayload?): Boolean

    fun shareIntent(context: Context, track: Track, style: String): Intent?
}

internal class TrackShareManagerOperations(
    private val trackShareManager: TrackShareManager?,
    private val nativeMusicShareManager: NativeMusicShareManager?
) : TrackShareOperations {
    override fun isShareAvailable(): Boolean =
        trackShareManager != null

    override fun musicSharePayload(track: Track): TrackMusicSharePayload? =
        trackShareManager?.musicSharePayload(track)

    override fun shareNative(activity: Activity, track: Track, payload: TrackMusicSharePayload?): Boolean =
        nativeMusicShareManager?.share(activity, track, payload) == true

    override fun shareIntent(context: Context, track: Track, style: String): Intent? =
        trackShareManager?.share(context, track, style)
}

internal class TrackShareLauncher @JvmOverloads constructor(
    private val activity: Activity,
    private val operations: TrackShareOperations,
    private val languageProvider: TrackShareLanguageProvider,
    private val shareStyleProvider: TrackShareStyleProvider,
    private val statusSink: TrackShareStatusSink,
    private val activityStarter: TrackShareActivityStarter = TrackShareActivityStarter { intent ->
        activity.startActivity(intent)
    }
) {
    fun share(track: Track?) {
        if (track == null) {
            statusSink.showFeedback(AppLanguage.text(languageProvider.languageMode(), "no.track.selected"))
            return
        }
        if (!operations.isShareAvailable()) {
            statusSink.showFeedback("分享服务暂不可用")
            return
        }
        try {
            val shareStyle = TrackShareStyle.normalize(shareStyleProvider.shareStyle())
            statusSink.showFeedback("正在打开分享面板：${track.title}")
            if (shareStyle == TrackShareStyle.PLATFORM_CARD &&
                operations.shareNative(activity, track, operations.musicSharePayload(track))
            ) {
                statusSink.setStatus("已发起原生音乐卡片分享：${track.title}")
                return
            }
            val send = operations.shareIntent(activity, track, shareStyle)
            if (send == null) {
                statusSink.showFeedback("分享服务暂不可用")
                return
            }
            activityStarter.startActivity(Intent.createChooser(send, "分享到"))
            statusSink.setStatus("已打开分享面板：${track.title}")
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to share track", error)
            statusSink.showFeedback("无法打开分享面板")
        }
    }

    private companion object {
        private const val TAG = "TrackShareLauncher"
    }
}
