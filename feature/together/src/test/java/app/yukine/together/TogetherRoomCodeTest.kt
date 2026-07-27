package app.yukine.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherRoomCodeTest {
    @Test
    fun acceptsNormalizedJuntoBech32Secret() {
        assertTrue(TogetherRoomCode.isValid("  JUN1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq  "))
    }

    @Test
    fun rejectsWrongPrefixAndForbiddenCharacters() {
        assertFalse(TogetherRoomCode.isValid("room1qqqqqqqqqqqqqqqqqqqqqqqq"))
        assertFalse(TogetherRoomCode.isValid("jun1oooooooooooooooooooooooo"))
        assertFalse(TogetherRoomCode.isValid("jun1short"))
    }

    @Test
    fun extractFromTextFindsEmbeddedRoomCode() {
        val code = "jun1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq"
        assertEquals(
            code,
            TogetherRoomCode.extractFromText("Join me: $code thanks!")
        )
        assertEquals(
            code,
            TogetherRoomCode.extractFromText("  JUN1QQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ  ")
        )
    }

    @Test
    fun extractFromTextReturnsNullWhenMissing() {
        assertNull(TogetherRoomCode.extractFromText(""))
        assertNull(TogetherRoomCode.extractFromText("no room here"))
        assertNull(TogetherRoomCode.extractFromText("jun1short"))
    }
}
