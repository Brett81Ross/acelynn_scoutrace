package com.cactusbyte.scouttrace

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineNarrativeTest {
    @Test fun noBaselineExplainsNextStep() {
        val r = BaselineNarrative.summarize(JSONObject().put("exists", false))
        assertFalse(r.getBoolean("changed"))
        assertEquals("No trusted baseline saved", r.getString("headline"))
    }

    @Test fun unchangedBaselineIsClear() {
        val comparison = JSONObject().put("exists", true)
            .put("newApps", JSONArray()).put("removedApps", JSONArray()).put("riskChanges", JSONArray())
        val r = BaselineNarrative.summarize(comparison)
        assertFalse(r.getBoolean("changed"))
        assertEquals(0, r.getInt("changeCount"))
    }

    @Test fun riskChangeIsPrioritizedInHeadline() {
        val comparison = JSONObject().put("exists", true)
            .put("newApps", JSONArray()).put("removedApps", JSONArray())
            .put("riskChanges", JSONArray().put(JSONObject().put("packageName", "example")))
        val r = BaselineNarrative.summarize(comparison)
        assertTrue(r.getBoolean("changed"))
        assertEquals(1, r.getInt("riskChanges"))
    }
}
