package app.yukine.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the cookie-capture validation used by the local WebView login fallback. The WebView itself
 * needs an instrumented test, but the domain list and session-token validation are pure logic and
 * are the part that previously caused "fake" cookies (querying the wrong domain / accepting cookies
 * with no real session token).
 */
class LocalStreamingLoginEndpointsTest {

    @Test
    fun netEaseCookieDomainsIncludeRegistrableParentForHttpOnlyToken() {
        val domains = LocalStreamingLoginEndpoints.cookieDomainHints(StreamingProviderName.NETEASE)
        // The session token MUSIC_U lives on .163.com, not on the music.163.com login page only.
        assertTrue(domains.any { it.contains("163.com") })
        assertTrue(domains.size >= 2)
    }

    @Test
    fun bilibiliCookieDomainsCoverParentDomainNotJustLoginSubdomain() {
        val domains = LocalStreamingLoginEndpoints.cookieDomainHints(StreamingProviderName.BILIBILI)
        // SESSDATA is set on .bilibili.com; the login page is passport.bilibili.com.
        assertTrue(domains.any { it.contains("www.bilibili.com") || it.contains("//bilibili.com") })
    }

    @Test
    fun qqMusicCookieDomainsCoverParentDomainAndMusicSubdomains() {
        val domains = LocalStreamingLoginEndpoints.cookieDomainHints(StreamingProviderName.QQ_MUSIC)

        assertTrue(domains.any { it.contains("portal/pop_login.html") })
        assertTrue(domains.any { it.contains("m.y.qq.com") || it.contains("i.y.qq.com/n2/m") })
        assertTrue(domains.any { it.contains("y.qq.com") })
        assertTrue(domains.any { it.contains("//qq.com") || it.contains("www.qq.com") })
    }

    @Test
    fun qqMusicLoginUsesQqMobileScanPage() {
        val url = LocalStreamingLoginEndpoints.loginUrl(
            StreamingProviderName.QQ_MUSIC,
            "echo-next://streaming-auth"
        ).orEmpty()

        assertTrue(url.contains("y.qq.com/portal/pop_login.html"))
        assertFalse(url.contains("portal/profile.html"))
        assertFalse(url.contains("wkframe/client/login.html"))
    }

    @Test
    fun hasSessionTokenTrueOnlyWhenCredentialCookiePresent() {
        // Anonymous/visitor cookies must NOT count as a logged-in session.
        assertFalse(
            LocalStreamingLoginEndpoints.hasSessionToken(
                StreamingProviderName.NETEASE,
                listOf("NMTID", "_ntes_nnid", "WEVNSM")
            )
        )
        // The real credential makes it a valid session.
        assertTrue(
            LocalStreamingLoginEndpoints.hasSessionToken(
                StreamingProviderName.NETEASE,
                listOf("NMTID", "MUSIC_U", "__csrf")
            )
        )
    }

    @Test
    fun neteaseCsrfAloneDoesNotProveAnAccountSession() {
        assertFalse(
            LocalStreamingLoginEndpoints.hasSessionToken(
                StreamingProviderName.NETEASE,
                listOf("__csrf")
            )
        )
        assertTrue(
            LocalStreamingLoginEndpoints.hasSessionToken(
                StreamingProviderName.NETEASE,
                listOf("__csrf", "MUSIC_U")
            )
        )
    }

    @Test
    fun hasSessionTokenTrimsCookieNames() {
        assertTrue(
            LocalStreamingLoginEndpoints.hasSessionToken(
                StreamingProviderName.BILIBILI,
                listOf("  SESSDATA  ")
            )
        )
    }

    @Test
    fun providersWithoutKnownTokenNamesAreNotBlocked() {
        // MOCK has no session-token names configured; treat as satisfied so login is not blocked.
        assertTrue(
            LocalStreamingLoginEndpoints.hasSessionToken(
                StreamingProviderName.MOCK,
                emptyList()
            )
        )
    }

    @Test
    fun sessionTokenNamesAreDefinedForCookieProviders() {
        assertTrue(LocalStreamingLoginEndpoints.sessionTokenNames(StreamingProviderName.NETEASE).contains("MUSIC_U"))
        assertTrue(LocalStreamingLoginEndpoints.sessionTokenNames(StreamingProviderName.QQ_MUSIC).contains("qqmusic_key"))
        assertEquals(listOf("SESSDATA"), LocalStreamingLoginEndpoints.sessionTokenNames(StreamingProviderName.BILIBILI))
    }

    @Test
    fun qqMusicSessionRequiresCredentialCookieNotOnlyUin() {
        assertFalse(
            LocalStreamingLoginEndpoints.hasSessionToken(
                StreamingProviderName.QQ_MUSIC,
                listOf("uin", "p_uin")
            )
        )
        assertTrue(
            LocalStreamingLoginEndpoints.hasSessionToken(
                StreamingProviderName.QQ_MUSIC,
                listOf("uin", "qm_keyst")
            )
        )
    }
}
