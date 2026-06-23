package app.yukine

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageOnboardingTest {
    @Test
    fun onboardingBrandAndActionsHaveChineseAndEnglishLabels() {
        assertEquals("YUKINE", AppLanguage.text(AppLanguage.MODE_ENGLISH, "app.name"))
        assertEquals("YUKINE", AppLanguage.text(AppLanguage.MODE_CHINESE, "app.name"))
        assertEquals("Enter YUKINE", AppLanguage.text(AppLanguage.MODE_ENGLISH, "onboarding.enter"))
        assertEquals("\u8fdb\u5165 YUKINE", AppLanguage.text(AppLanguage.MODE_CHINESE, "onboarding.enter"))
        assertEquals("Grant", AppLanguage.text(AppLanguage.MODE_ENGLISH, "onboarding.action.grant"))
        assertEquals("\u53bb\u6388\u6743", AppLanguage.text(AppLanguage.MODE_CHINESE, "onboarding.action.grant"))
    }

    @Test
    fun onboardingMissingListUsesLocalizedSeparator() {
        assertEquals(", ", AppLanguage.text(AppLanguage.MODE_ENGLISH, "onboarding.missing.separator"))
        assertEquals("\u3001", AppLanguage.text(AppLanguage.MODE_CHINESE, "onboarding.missing.separator"))
        assertEquals(
            "You cannot enter yet. Finish: ",
            AppLanguage.text(AppLanguage.MODE_ENGLISH, "onboarding.missing.prefix")
        )
        assertEquals(
            "\u8fd8\u4e0d\u80fd\u8fdb\u5165\u3002\u8bf7\u5148\u5b8c\u6210\uff1a",
            AppLanguage.text(AppLanguage.MODE_CHINESE, "onboarding.missing.prefix")
        )
    }
}
