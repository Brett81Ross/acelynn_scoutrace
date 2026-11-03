package com.cactusbyte.scouttrace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OemEvidenceGuidanceTest {
    @Test fun samsungPixelMotorolaAndUnknownAllUseHonestGenericSettings() {
        for (oem in listOf("samsung", "Google", "motorola", "xiaomi", "", null)) {
            assertEquals("GENERIC_ANDROID_SETTINGS", OemEvidenceGuidance.settingsMode(oem))
            assertTrue(OemEvidenceGuidance.note(oem).contains("vary"))
        }
    }
}
