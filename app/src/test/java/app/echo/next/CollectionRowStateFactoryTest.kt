package app.echo.next

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionRowStateFactoryTest {
    @Test
    fun trackCountLabelUsesLanguageTable() {
        assertEquals("1 track", CollectionRowStateFactory.trackCountLabel(1, AppLanguage.MODE_ENGLISH))
        assertEquals("2 tracks", CollectionRowStateFactory.trackCountLabel(2, AppLanguage.MODE_ENGLISH))
        assertEquals("1 首歌曲", CollectionRowStateFactory.trackCountLabel(1, AppLanguage.MODE_CHINESE))
        assertEquals("2 首歌曲", CollectionRowStateFactory.trackCountLabel(2, AppLanguage.MODE_CHINESE))
    }
}
