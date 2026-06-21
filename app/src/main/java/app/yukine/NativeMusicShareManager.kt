package app.yukine

import android.app.Activity
import android.os.Bundle
import app.yukine.model.Track
import app.yukine.streaming.StreamingProviderName
import com.tencent.mm.opensdk.modelmsg.SendMessageToWX
import com.tencent.mm.opensdk.modelmsg.WXMediaMessage
import com.tencent.mm.opensdk.modelmsg.WXMusicObject
import com.tencent.mm.opensdk.openapi.WXAPIFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeMusicShareManager @Inject constructor() {
    fun share(activity: Activity, track: Track, payload: TrackMusicSharePayload?): Boolean {
        if (payload == null) {
            return false
        }
        return shareToWechat(activity, track, payload) || shareToQq(activity, track, payload)
    }

    private fun shareToWechat(activity: Activity, track: Track, payload: TrackMusicSharePayload): Boolean {
        val appId = BuildConfig.YUKINE_WECHAT_APP_ID.takeIf { it.isNotBlank() } ?: return false
        val api = WXAPIFactory.createWXAPI(activity, appId, true)
        if (!api.registerApp(appId) || !api.isWXAppInstalled) {
            return false
        }
        val music = WXMusicObject().apply {
            musicUrl = payload.url
            musicDataUrl = track.contentUri.toString().takeIf { it.startsWith("http", ignoreCase = true) } ?: payload.url
        }
        val message = WXMediaMessage(music).apply {
            title = track.title
            description = track.artist
        }
        val request = SendMessageToWX.Req().apply {
            transaction = "yukine_music_${System.currentTimeMillis()}"
            this.message = message
            scene = SendMessageToWX.Req.WXSceneSession
        }
        return runCatching { api.sendReq(request) }.getOrDefault(false)
    }

    private fun shareToQq(activity: Activity, track: Track, payload: TrackMusicSharePayload): Boolean {
        val appId = BuildConfig.YUKINE_QQ_APP_ID.takeIf { it.isNotBlank() } ?: return false
        return runCatching {
            val tencentClass = Class.forName("com.tencent.tauth.Tencent")
            val qqShareClass = Class.forName("com.tencent.connect.share.QQShare")
            val iUiListenerClass = Class.forName("com.tencent.tauth.IUiListener")
            val tencent = tencentClass
                .getMethod("createInstance", String::class.java, android.content.Context::class.java)
                .invoke(null, appId, activity)
            val params = Bundle().apply {
                putInt("req_type", qqShareClass.getField("SHARE_TO_QQ_TYPE_AUDIO").getInt(null))
                putString("title", track.title)
                putString("summary", track.artist)
                putString("targetUrl", payload.url)
                putString("audio_url", track.contentUri.toString().takeIf { it.startsWith("http", ignoreCase = true) } ?: payload.url)
                putString("imageUrl", track.albumArtUri.toString().takeIf { it.startsWith("http", ignoreCase = true) })
                putString("appName", "Yukine")
            }
            tencentClass
                .getMethod("shareToQQ", Activity::class.java, Bundle::class.java, iUiListenerClass)
                .invoke(tencent, activity, params, null)
            true
        }.getOrDefault(false)
    }
}
