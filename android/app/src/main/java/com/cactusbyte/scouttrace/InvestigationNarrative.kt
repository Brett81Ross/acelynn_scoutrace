package com.cactusbyte.scouttrace

import org.json.JSONArray
import org.json.JSONObject

object InvestigationNarrative {
    fun build(result: JSONObject): JSONObject {
        val findings = result.optJSONArray("findings") ?: JSONArray()
        val top = JSONArray()
        for (i in 0 until minOf(findings.length(), 5)) {
            val item = findings.optJSONObject(i) ?: continue
            top.put(JSONObject()
                .put("level", item.optString("level", "REVIEW"))
                .put("title", item.optString("title", "Finding"))
                .put("detail", item.optString("detail", "Review this observation.")))
        }
        val baseline = result.optJSONObject("baseline") ?: JSONObject()
        val changes = baseline.optJSONObject("narrative") ?: JSONObject()
        val level = result.optString("level", "CLEAR")
        val headline = when (level) {
            "HIGH CAUTION" -> "Correlated security indicators need attention"
            "ELEVATED" -> "Multiple security indicators should be reviewed"
            "REVIEW" -> "ScoutTrace found items worth reviewing"
            else -> "No elevated indicators found in the available evidence"
        }
        return JSONObject()
            .put("headline", headline)
            .put("status", level)
            .put("priorities", top)
            .put("securityDiff", changes.optString("headline", "No comparison available"))
            .put("securityDiffDetail", changes.optString("detail", ""))
            .put("recommendedAction", if (top.length() > 0) "Review the highest-priority finding and verify whether its permissions and special access are expected." else "Save a trusted baseline and review future changes.")
            .put("priorityCount", top.length())
            .put("residualRisk", "ScoutTrace cannot prove a device is malware-free. Android may limit package visibility and some security state is not exposed to third-party apps.")
    }
}
