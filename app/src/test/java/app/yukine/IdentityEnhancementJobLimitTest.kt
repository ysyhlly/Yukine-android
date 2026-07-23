package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class IdentityEnhancementJobLimitTest {
    @Test
    fun onDemandRunsStaySmall() {
        assertEquals(5, identityEnhancementJobLimit(onDemand = true))
    }

    @Test
    fun backgroundRunsUseBoundedBatch() {
        assertEquals(100, identityEnhancementJobLimit(onDemand = false))
    }
}
