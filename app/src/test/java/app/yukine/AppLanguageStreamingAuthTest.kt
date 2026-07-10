package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageStreamingAuthTest {
    @Test
    fun qqWebLoginRiskConfirmationHasChineseAndEnglishCopy() {
        assertEquals(
            "QQ Music sign-in risk notice",
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.web.auth.qq.risk.title")
        )
        assertEquals(
            "QQ 音乐登录风险提示",
            AppLanguage.text(AppLanguage.MODE_CHINESE, "streaming.web.auth.qq.risk.title")
        )
        assertEquals(
            "Please wait 5 s",
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "streaming.web.auth.qq.risk.countdown").replace("%d", "5")
        )
        assertEquals(
            "我已了解，继续",
            AppLanguage.text(AppLanguage.MODE_CHINESE, "streaming.web.auth.qq.risk.continue")
        )
        assertTrue(
            AppLanguage.text(AppLanguage.MODE_CHINESE, "streaming.web.auth.qq.risk.message")
                .contains("当前网络访问受限")
        )
    }
}
