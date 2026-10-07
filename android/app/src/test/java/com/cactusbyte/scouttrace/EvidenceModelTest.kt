package com.cactusbyte.scouttrace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceModelTest {
    @Test fun singleIndicatorStaysLowConfidence() {
        val r = EvidenceModel.assess(true, false, false, false, false, false)
        assertEquals("LOW", r.getString("confidence"))
        assertFalse(r.getBoolean("correlated"))
        assertEquals(1, r.getInt("indicatorCount"))
    }

    @Test fun accessibilityPlusOverlayCorrelates() {
        val r = EvidenceModel.assess(false, true, false, true, false, false)
        assertEquals("HIGH", r.getString("confidence"))
        assertTrue(r.getBoolean("correlated"))
        assertEquals(2, r.getInt("indicatorCount"))
    }

    @Test fun noIndicatorsIsInsufficient() {
        val r = EvidenceModel.assess(false, false, false, false, false, false)
        assertEquals("INSUFFICIENT", r.getString("confidence"))
        assertFalse(r.getBoolean("correlated"))
    }
}
