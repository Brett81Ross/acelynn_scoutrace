package com.cactusbyte.scouttrace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SecurityPatchReviewTest {
    private val today = LocalDate.of(2026, 10, 13)

    @Test fun recentDateIsNotMarkedAsOutdated() {
        val result = SecurityPatchReview.assess("2026-10-01", today)
        assertEquals(SecurityPatchReview.Status.RECENT_DATE, result.status)
        assertFalse(result.reviewRecommended)
        assertEquals(12L, result.ageDays)
    }

    @Test fun oldPatchDateSuggestsReviewWithoutClaimingMalware() {
        val result = SecurityPatchReview.assess("2026-01-01", today)
        assertEquals(SecurityPatchReview.Status.OLDER_DATE, result.status)
        assertTrue(result.reviewRecommended)
        assertTrue(result.detail.contains("does not prove"))
    }

    @Test fun missingOrMalformedLevelDoesNotBecomeAnOutdatedDateClaim() {
        assertEquals(SecurityPatchReview.Status.UNAVAILABLE, SecurityPatchReview.assess("", today).status)
        assertEquals(SecurityPatchReview.Status.UNRECOGNIZED, SecurityPatchReview.assess("unknown", today).status)
    }

    @Test fun futureDateCallsForClockCheckNotMalwareAlarm() {
        val result = SecurityPatchReview.assess("2027-01-01", today)
        assertEquals(SecurityPatchReview.Status.FUTURE_DATED, result.status)
        assertFalse(result.reviewRecommended)
    }

    @Test fun boundaryIsAt180Days() {
        val atBoundary = today.minusDays(180)
        val newer = today.minusDays(179)
        assertTrue(SecurityPatchReview.assess(atBoundary.toString(), today).reviewRecommended)
        assertFalse(SecurityPatchReview.assess(newer.toString(), today).reviewRecommended)
    }
}
