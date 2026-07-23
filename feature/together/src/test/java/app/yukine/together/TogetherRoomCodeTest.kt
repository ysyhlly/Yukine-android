package app.yukine.together

import org.junit.Assert.assertFalse
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
}
