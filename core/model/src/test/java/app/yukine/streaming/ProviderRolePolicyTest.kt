package app.yukine.streaming

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRolePolicyTest {
    @Test
    fun identityPlaybackAndSyncRolesAreMutuallyConsistent() {
        listOf("local", "document", "webdav").forEach { provider ->
            assertTrue(ProviderRolePolicy.contributesIdentity(provider))
            assertTrue(ProviderRolePolicy.isPhysical(provider))
            assertTrue(ProviderRolePolicy.canEverBecomeActive(provider))
        }

        assertTrue(ProviderRolePolicy.contributesIdentity("netease"))
        assertTrue(ProviderRolePolicy.canBecomeActive("netease", true, false))
        assertFalse(ProviderRolePolicy.canBecomeActive("netease", false, false))
        assertFalse(ProviderRolePolicy.canBecomeActive("netease", true, true))

        assertTrue(ProviderRolePolicy.contributesIdentity("qqmusic"))
        assertTrue(ProviderRolePolicy.canSyncFavorites("qqmusic"))
        assertTrue(ProviderRolePolicy.canSyncPlaylists("qqmusic"))
        assertFalse(ProviderRolePolicy.canEverBecomeActive("qqmusic"))

        assertTrue(ProviderRolePolicy.isPlaybackResolver("luoxue"))
        assertFalse(ProviderRolePolicy.contributesIdentity("luoxue"))
        assertFalse(ProviderRolePolicy.canPersistCanonicalSource("luoxue"))
        assertFalse(ProviderRolePolicy.canSyncFavorites("luoxue"))
        assertFalse(ProviderRolePolicy.canSyncPlaylists("luoxue"))
        assertFalse(ProviderRolePolicy.canEverBecomeActive("luoxue"))

        assertTrue(ProviderRolePolicy.canPersistCanonicalSource("kugou"))
        assertTrue(ProviderRolePolicy.isPlaybackResolver("kugou"))
        assertTrue(ProviderRolePolicy.canSyncFavorites("kugou"))
        assertTrue(ProviderRolePolicy.canSyncPlaylists("kugou"))
        assertTrue(ProviderRolePolicy.canBecomeActive("kugou", false, false))
        assertFalse(ProviderRolePolicy.canBecomeActive("kugou", false, true))
        assertFalse(ProviderRolePolicy.canPersistCanonicalSource("unknown"))
    }
}
