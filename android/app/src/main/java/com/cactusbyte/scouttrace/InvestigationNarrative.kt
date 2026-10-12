package com.cactusbyte.scouttrace

import org.json.JSONArray
import org.json.JSONObject

/** Evidence-based prioritization; no synthetic probability or malware verdicts. */
object InvestigationNarrative {
    private fun severity(level: String): Int = when (level) {
        "HIGH CAUTION" -> 4
        "ELEVATED" -> 3
        "REVIEW" -> 2
        else -> 1
    }

    fun build(result: JSONObject): JSONObject {
        val findings = result.optJSONArray("findings") ?: JSONArray()
        val dispositions = result.optJSONObject("dispositions") ?: JSONObject()
        val candidates = (0 until findings.length()).mapNotNull { findings.optJSONObject(it) }
        val sorted = candidates.sortedWith(
            compareByDescending<JSONObject> { severity(it.optString("level")) }
                .thenByDescending { dispositions.optString(it.optString("packageName")) == "WATCH" }
                .thenBy { it.optString("packageName") }
                .thenBy { it.optString("title") }
        )
        val priorities = JSONArray()
        sorted.filter {
            it.optString("level") == "HIGH CAUTION" ||
                dispositions.optString(it.optString("packageName")) != "IGNORE"
        }.take(5).forEach { item ->
            val remediation = item.optJSONObject("remediation")
            priorities.put(JSONObject()
                .put("level", item.optString("level", "REVIEW"))
                .put("title", item.optString("title", "Finding"))
                .put("detail", item.optString("detail", "Review this observation."))
                .put("packageName", item.optString("packageName"))
                .put("nextStep", remediation?.optString("nextStep", "Review the evidence and verify expected behavior.")
                    ?: "Review the evidence and verify expected behavior."))
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
            .put("priorities", priorities)
            .put("securityDiff", changes.optString("headline", "No comparison available"))
            .put("securityDiffDetail", changes.optString("detail", ""))
            .put("recommendedAction", if (priorities.length() > 0)
                priorities.getJSONObject(0).optString("nextStep")
                else "Save a trusted baseline and review future changes.")
            .put("priorityCount", priorities.length())
            .put("correlationNote", "Priority order combines observed severity and your Watch classifications. This is triage, not proof of compromise.")
            .put("residualRisk", "ScoutTrace cannot prove a device is malware-free. Android may limit package visibility and some security state is not exposed to third-party apps.")
    }
}
