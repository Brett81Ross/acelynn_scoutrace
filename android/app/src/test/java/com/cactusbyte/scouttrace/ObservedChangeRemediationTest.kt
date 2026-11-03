package com.cactusbyte.scouttrace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservedChangeRemediationTest {
    @Test fun activePrivilegeGuideIncludesSafetyPlanning() {
        val r=RemediationGuide.forObservedChange("PRIORITY_REVIEW","com.example.app")
        assertTrue(r.getString("nextStep").contains("Accessibility"))
        assertTrue(r.getString("note").contains("personal safety"))
        assertFalse(r.getBoolean("automaticActionTaken"))
    }

    @Test fun grantGuideNeverCallsItSpyware() {
        val r=RemediationGuide.forObservedChange("REVIEW_NEW_GRANT","com.example.app")
        assertTrue(r.getString("nextStep").contains("Android App Info"))
        assertTrue(r.getString("note").contains("not proof"))
    }

    @Test fun declarationGuideDoesNotClaimActiveAccess() {
        val r=RemediationGuide.forObservedChange("CONTEXT_ONLY","com.example.app")
        assertTrue(r.getString("nextStep").contains("declaration"))
        assertFalse(r.getBoolean("automaticActionTaken"))
    }

    @Test fun nullPackageRemainsNull() {
        val r=RemediationGuide.forObservedChange("REVIEW_ACTIVE_ACCESS",null)
        assertTrue(r.isNull("packageName"))
        assertEquals("REVIEW_ACTIVE_ACCESS",r.getString("priority"))
    }

    @Test fun listenerSettingHasDistinctSafeGuidance() {
        val result=RemediationGuide.forObservedChange("REVIEW_ENABLED_SETTING","com.example.app")
        assertTrue(result.getString("nextStep").contains("notification access settings"))
        assertTrue(result.getString("nextStep").contains("does not prove"))
        assertFalse(result.getBoolean("automaticActionTaken"))
    }
}
