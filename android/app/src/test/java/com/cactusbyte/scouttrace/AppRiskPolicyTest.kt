package com.cactusbyte.scouttrace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRiskPolicyTest {
    @Test fun manifestDeclarationsAndInstallerContextNeverBecomeElevatedWithoutActiveAccess() {
        val result = AppRiskPolicy.evaluate(AppRiskPolicy.Signals(
            otherInstaller = true, overlayDeclared = true,
            installRequestsDeclared = true, debuggable = true))
        assertEquals("REVIEW", result.level)
        assertEquals(0, result.activePrivilegeCount)
        assertEquals(2, result.declaredCapabilityCount)
        assertEquals("DECLARATIONS_AND_CONTEXT_ONLY", result.rule)
    }

    @Test fun activeAccessibilityPlusOverlayDeclarationWarrantsReviewPriorityNotMalwareVerdict() {
        val result = AppRiskPolicy.evaluate(AppRiskPolicy.Signals(
            accessibilityEnabled = true, overlayDeclared = true))
        assertEquals("ELEVATED", result.level)
        assertEquals("ACTIVE_PRIVILEGE_WITH_CONTEXT", result.rule)
        assertTrue(result.reasons.any { it.contains("not verified") })
    }

    @Test fun activeAdministratorWithAlternativeInstallerIsElevatedButNotStrongCorrelation() {
        val result = AppRiskPolicy.evaluate(AppRiskPolicy.Signals(
            deviceAdminActive = true, otherInstaller = true))
        assertEquals("ELEVATED", result.level)
        assertEquals(1, result.activePrivilegeCount)
    }

    @Test fun twoObservedActivePrivilegesProduceHighCaution() {
        val result = AppRiskPolicy.evaluate(AppRiskPolicy.Signals(
            accessibilityEnabled = true, deviceAdminActive = true))
        assertEquals("HIGH CAUTION", result.level)
        assertEquals("TWO_ACTIVE_PRIVILEGES", result.rule)
        assertEquals(2, result.activePrivilegeCount)
    }

    @Test fun onlyOneActivePrivilegeIsReviewNotAutomaticallyHighCaution() {
        val result = AppRiskPolicy.evaluate(AppRiskPolicy.Signals(accessibilityEnabled = true))
        assertEquals("REVIEW", result.level)
        assertEquals("SINGLE_ACTIVE_PRIVILEGE", result.rule)
    }

    @Test fun noIndicatorsDoNotProveDeviceSafety() {
        val result = AppRiskPolicy.evaluate(AppRiskPolicy.Signals())
        assertEquals("CLEAR", result.level)
        assertEquals("NO_LISTED_INDICATORS", result.rule)
        assertTrue(result.reasons.isEmpty())
    }
}
