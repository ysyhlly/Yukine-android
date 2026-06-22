package app.yukine

import app.yukine.streaming.StreamingAuthState
import app.yukine.streaming.StreamingProviderHealth
import app.yukine.streaming.StreamingProviderName
import app.yukine.streaming.StreamingProviderStatus
import app.yukine.ui.StreamingSearchLabels
import app.yukine.ui.streamingProviderStatusText
import app.yukine.ui.streamingStatusMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingSearchScreenStatusTest {
    @Test
    fun mapsCanonicalStreamingStatusMessagesToLocalizedLabels() {
        val labels = StreamingSearchLabels.empty().copy(
            localLoginSaved = "本地登录已保存",
            notSignedIn = "未登录",
            localLoginComplete = "本地登录成功",
            gatewayLocalLogin = "Gateway 不可用，已使用本地登录",
            gatewayRequired = "需要配置流媒体网关后才能使用",
            loginEntryMissing = "未配置该音源的登录入口",
            openLoginPage = "请在新页面登录",
            neteaseLikedPlaylistEmpty = "网易云喜欢歌单为空",
            neteaseAccountIdMissing = "无法读取网易云账号 ID",
            neteaseLoginRequiredPlaylists = "请先登录网易云"
        )

        assertEquals("本地登录已保存", streamingStatusMessage("Local login saved", labels))
        assertEquals("本地登录已保存", streamingStatusMessage("Saved local login", labels))
        assertEquals("未登录", streamingStatusMessage("Not signed in", labels))
        assertEquals("本地登录成功", streamingStatusMessage("Local login complete", labels))
        assertEquals("Gateway 不可用，已使用本地登录", streamingStatusMessage("Gateway unavailable; using local login", labels))
        assertEquals("需要配置流媒体网关后才能使用", streamingStatusMessage("Streaming gateway required", labels))
        assertEquals("未配置该音源的登录入口", streamingStatusMessage("No sign-in entry is configured for this source", labels))
        assertEquals("请在新页面登录", streamingStatusMessage("Open the sign-in page", labels))
        assertEquals("网易云喜欢歌单为空", streamingStatusMessage("NetEase liked playlist is empty; heartbeat recommendations cannot be generated.", labels))
        assertEquals("无法读取网易云账号 ID", streamingStatusMessage("Could not read NetEase account ID; sign in again before loading account playlists.", labels))
        assertEquals("请先登录网易云", streamingStatusMessage("Sign in to NetEase before loading playlists.", labels))
        assertEquals("Gateway is not connected", streamingStatusMessage("Gateway is not connected", labels))
        assertNull(streamingStatusMessage("", labels))
    }

    @Test
    fun providerStatusKeepsReadyLocalSourcesSeparateFromSignedInState() {
        val labels = StreamingSearchLabels.empty().copy(
            signedIn = "已登录",
            onlineAuthenticated = "在线，已认证",
            online = "在线",
            ready = "就绪"
        )

        val status = streamingProviderStatusText(
            message = "本机直连，支持搜索、播放和歌单导入",
            status = StreamingProviderStatus.READY,
            health = StreamingProviderHealth(
                provider = StreamingProviderName.QQ_MUSIC,
                available = true,
                authenticated = false
            ),
            authState = StreamingAuthState(connected = false),
            labels = labels
        )

        assertEquals("本机直连，支持搜索、播放和歌单导入", status)
    }

    @Test
    fun providerStatusStillShowsSignedInWhenAuthStateIsConnected() {
        val labels = StreamingSearchLabels.empty().copy(signedIn = "已登录")

        val status = streamingProviderStatusText(
            message = "本机直连，支持搜索、播放和歌单导入",
            status = StreamingProviderStatus.READY,
            health = StreamingProviderHealth(
                provider = StreamingProviderName.NETEASE,
                available = true,
                authenticated = false
            ),
            authState = StreamingAuthState(connected = true, accountDisplayName = "Yukine"),
            labels = labels
        )

        assertEquals("已登录 - Yukine", status)
    }
}
