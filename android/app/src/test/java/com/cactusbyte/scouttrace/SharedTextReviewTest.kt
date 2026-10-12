package com.cactusbyte.scouttrace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTextReviewTest {
    @Test fun shareInputIsBounded() {
        assertEquals(SharedTextReview.MAX_CHARS, SharedTextReview.normalize("a".repeat(7000))?.length)
    }

    @Test fun blankAndMissingInputAreIgnored() {
        assertNull(SharedTextReview.normalize(null))
        assertNull(SharedTextReview.normalize("   "))
    }

    @Test fun inputIsPreservedForLocalHumanReview() {
        val text = "Urgent: verify this link at https://example.invalid/"
        assertEquals(text, SharedTextReview.normalize(text))
    }
}
