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

    @Test fun clearAppWithNewSensitiveGrantRemainsVisibleForReview() {
        val baseline=JSONObject().put("exists",true).put("metadataChanges",JSONArray()
            .put(JSONObject().put("packageName","com.example.clear")
                .put("grantsAdded",JSONArray().put("android.permission.CAMERA"))))
        val input=JSONObject().put("level","CLEAR").put("findings",JSONArray())
            .put("appRisks",JSONArray().put(JSONObject().put("packageName","com.example.clear").put("level","CLEAR")))
            .put("baseline",baseline)
        val output=InvestigationNarrative.build(input)
        assertEquals(0,output.getInt("priorityCount"))
        assertEquals(1,output.getInt("visibleBaselineChangePriorityCount"))
        assertEquals("REVIEW_NEW_GRANT",output.getJSONArray("visibleBaselineChangePriorities")
            .getJSONObject(0).getString("priority"))
    }

    @Test fun ignoredLowSeverityChangeIsHiddenButHighCautionRemainsVisible() {
        val baseline=JSONObject().put("exists",true).put("metadataChanges",JSONArray()
            .put(JSONObject().put("packageName","com.example.ignored")
                .put("grantsAdded",JSONArray().put("android.permission.CAMERA")))
            .put(JSONObject().put("packageName","com.example.high")
                .put("accessEnabled",JSONArray().put("accessibilityEnabled"))))
        val input=JSONObject().put("level","HIGH CAUTION").put("findings",JSONArray())
            .put("baseline",baseline).put("appRisks",JSONArray()
                .put(JSONObject().put("packageName","com.example.ignored").put("level","REVIEW"))
                .put(JSONObject().put("packageName","com.example.high").put("level","HIGH CAUTION")
                    .put("accessibilityEnabled",true).put("deviceAdminActive",true)))
            .put("dispositions",JSONObject().put("com.example.ignored","IGNORE")
                .put("com.example.high","IGNORE"))
        val result=InvestigationNarrative.build(input)
        assertEquals(2,result.getInt("baselineChangePriorityCount"))
        assertEquals(1,result.getInt("visibleBaselineChangePriorityCount"))
        assertEquals("com.example.high",result.getJSONArray("visibleBaselineChangePriorities")
            .getJSONObject(0).getString("packageName"))
    }

    @Test fun correlatedChangeBreaksSameSeverityTieBeforeWatch() {
        val change=JSONObject().put("packageName","com.example.changed")
            .put("grantsAdded",JSONArray().put("android.permission.RECORD_AUDIO"))
        val input=JSONObject().put("level","REVIEW")
            .put("findings",JSONArray()
                .put(finding("REVIEW","com.example.watched","Watch app"))
                .put(finding("REVIEW","com.example.changed","Check new grant")))
            .put("appRisks",JSONArray()
                .put(JSONObject().put("packageName","com.example.watched").put("level","REVIEW"))
                .put(JSONObject().put("packageName","com.example.changed").put("level","REVIEW")))
            .put("baseline",JSONObject().put("exists",true)
                .put("metadataChanges",JSONArray().put(change)))
            .put("dispositions",JSONObject().put("com.example.watched","WATCH"))
        val output=InvestigationNarrative.build(input)
        val first=output.getJSONArray("priorities").getJSONObject(0)
        assertEquals("com.example.changed",first.getString("packageName"))
        assertEquals("REVIEW_NEW_GRANT",first.getString("baselineChangePriority"))
        assertTrue(first.getString("nextStep").contains("Android App Info"))
    }

    @Test fun highCautionStillBeatsCorrelatedReviewAndIgnoredReviewStaysHidden() {
        val input=JSONObject().put("level","HIGH CAUTION")
            .put("findings",JSONArray()
                .put(finding("REVIEW","com.example.changed","Grant review"))
                .put(finding("HIGH CAUTION","com.example.urgent","Urgent review")))
            .put("appRisks",JSONArray().put(JSONObject()
                .put("packageName","com.example.changed").put("level","REVIEW")))
            .put("baseline",JSONObject().put("exists",true).put("metadataChanges",JSONArray()
                .put(JSONObject().put("packageName","com.example.changed")
                    .put("grantsAdded",JSONArray().put("android.permission.CAMERA")))))
            .put("dispositions",JSONObject().put("com.example.changed","IGNORE"))
        val output=InvestigationNarrative.build(input)
        assertEquals(1,output.getJSONArray("priorities").length())
        assertEquals("com.example.urgent",output.getJSONArray("priorities")
            .getJSONObject(0).getString("packageName"))
        assertEquals(0,output.getInt("visibleBaselineChangePriorityCount"))
    }

    @Test fun newlyEnabledNotificationListenerPrecedesWatchInSameSeverity() {
        val listener = "com.example.listener"
        val watched = "com.example.watched"
        val input = JSONObject().put("level", "REVIEW")
            .put("findings", JSONArray()
                .put(finding("REVIEW", watched, "Review watched"))
                .put(finding("REVIEW", listener, "Review listener setting")))
            .put("appRisks", JSONArray()
                .put(JSONObject().put("packageName", watched).put("level", "REVIEW"))
                .put(JSONObject().put("packageName", listener).put("level", "REVIEW")))
            .put("baseline", JSONObject().put("exists", true)
                .put("metadataChanges", JSONArray().put(JSONObject()
                    .put("packageName", listener)
                    .put("settingsEnabled", JSONArray().put("notificationListenerEnabled")))))
            .put("dispositions", JSONObject().put(watched, "WATCH"))
        val priorities = InvestigationNarrative.build(input).getJSONArray("priorities")
        assertEquals(listener, priorities.getJSONObject(0).getString("packageName"))
        assertEquals("REVIEW_ENABLED_SETTING", priorities.getJSONObject(0)
            .getString("baselineChangePriority"))
        assertEquals(watched, priorities.getJSONObject(1).getString("packageName"))
    }

    @Test fun declarationOnlyCorrelationPrecedesWatchWithoutMalwareVerdict() {
        val declared = "com.example.declared"
        val watched = "com.example.watched"
        val input = JSONObject().put("level", "REVIEW")
            .put("findings", JSONArray()
                .put(finding("REVIEW", watched, "Review watched"))
                .put(finding("REVIEW", declared, "Review declaration")))
            .put("appRisks", JSONArray()
                .put(JSONObject().put("packageName", watched).put("level", "REVIEW"))
                .put(JSONObject().put("packageName", declared).put("level", "REVIEW")))
            .put("baseline", JSONObject().put("exists", true)
                .put("metadataChanges", JSONArray().put(JSONObject()
                    .put("packageName", declared)
                    .put("specialDeclarationsAdded", JSONArray().put("vpnServiceDeclared")))))
            .put("dispositions", JSONObject().put(watched, "WATCH"))
        val output = InvestigationNarrative.build(input)
        val priorities = output.getJSONArray("priorities")
        assertEquals(declared, priorities.getJSONObject(0).getString("packageName"))
        assertEquals("CONTEXT_ONLY", priorities.getJSONObject(0).getString("baselineChangePriority"))
        assertEquals("REVIEW", priorities.getJSONObject(0).getString("level"))
        assertEquals(watched, priorities.getJSONObject(1).getString("packageName"))
    }

}
