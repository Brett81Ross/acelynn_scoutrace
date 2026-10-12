package com.cactusbyte.scouttrace

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestigationNarrativeTest {
    private fun finding(level: String, pkg: String, step: String): JSONObject =
        JSONObject().put("level", level).put("packageName", pkg)
            .put("title", "Review " + pkg).put("detail", "Observable indicator")
            .put("remediation", JSONObject().put("nextStep", step))

    @Test fun highCautionBeatsWatchedReview() {
        val input = JSONObject().put("level", "HIGH CAUTION")
            .put("findings", JSONArray()
                .put(finding("REVIEW", "com.example.watched", "Review watched"))
                .put(finding("HIGH CAUTION", "com.example.urgent", "Review urgent")))
            .put("dispositions", JSONObject().put("com.example.watched", "WATCH"))
        val result = InvestigationNarrative.build(input)
        assertEquals("com.example.urgent", result.getJSONArray("priorities").getJSONObject(0).getString("packageName"))
        assertEquals("Review urgent", result.getString("recommendedAction"))
    }

    @Test fun ignoredReviewIsExcludedButHighCautionRemains() {
        val input = JSONObject().put("findings", JSONArray()
            .put(finding("REVIEW", "com.example.ignored", "Ignore"))
            .put(finding("HIGH CAUTION", "com.example.urgent", "Review")))
            .put("dispositions", JSONObject()
                .put("com.example.ignored", "IGNORE")
                .put("com.example.urgent", "IGNORE"))
        val result = InvestigationNarrative.build(input)
        assertEquals(1, result.getJSONArray("priorities").length())
        assertEquals("com.example.urgent", result.getJSONArray("priorities").getJSONObject(0).getString("packageName"))
    }

    @Test fun emptyFindingsNeverClaimDeviceIsSafe() {
        val result = InvestigationNarrative.build(JSONObject().put("findings", JSONArray()))
        assertEquals(0, result.getInt("priorityCount"))
        assertTrue(result.getString("residualRisk").contains("cannot prove"))
    }
}
