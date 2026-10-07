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
            .put("priorityCount", top.length())
            .put("residualRisk", "ScoutTrace cannot prove a device is malware-free. Android may limit package visibility and some security state is not exposed to third-party apps.")
    }
}
