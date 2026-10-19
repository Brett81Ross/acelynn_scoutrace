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
        val baseline = result.optJSONObject("baseline") ?: JSONObject()
        val correlated = BaselineChangeCorrelation.correlate(baseline, result.optJSONArray("appRisks"))
        val correlationMap = (0 until correlated.length()).mapNotNull { i ->
            correlated.optJSONObject(i)?.let { event ->
                event.optString("packageName").takeIf(String::isNotBlank)?.let { it to event }
            }
        }.toMap()
        val dispositions = result.optJSONObject("dispositions") ?: JSONObject()
        val candidates = (0 until findings.length()).mapNotNull { findings.optJSONObject(it) }
        val sorted = candidates.sortedWith(
            compareByDescending<JSONObject> { severity(it.optString("level")) }
                .thenByDescending {
                    when(correlationMap[it.optString("packageName")]?.optString("priority")) {
                        "PRIORITY_REVIEW" -> 4
                        "REVIEW_ACTIVE_ACCESS" -> 3
                        "REVIEW_NEW_GRANT" -> 2
                        "CONTEXT_ONLY" -> 1
                        else -> 0
                    }
                }
                .thenByDescending { dispositions.optString(it.optString("packageName")) == "WATCH" }
                .thenBy { it.optString("packageName") }
                .thenBy { it.optString("title") }
        )
        val priorities = JSONArray()
        sorted.filter {
            it.optString("level") == "HIGH CAUTION" ||
                dispositions.optString(it.optString("packageName")) != "IGNORE"
        }.take(5).forEach { item ->
            val changed = correlationMap[item.optString("packageName")]
            val remediation = changed?.optJSONObject("remediation") ?: item.optJSONObject("remediation")
            priorities.put(JSONObject()
                .put("level", item.optString("level", "REVIEW"))
                .put("title", item.optString("title", "Finding"))
                .put("detail", item.optString("detail", "Review this observation."))
                .put("baselineChangePriority",changed?.optString("priority") ?: "NONE")
                .put("packageName", item.optString("packageName"))
                .put("nextStep", remediation?.optString("nextStep", "Review the evidence and verify expected behavior.")
                    ?: "Review the evidence and verify expected behavior."))
        }

        // Suppress ignored low-severity baseline changes, just like raw findings.
        val risks = result.optJSONArray("appRisks") ?: JSONArray()
        val visibleChanges = JSONArray()
        for (i in 0 until correlated.length()) {
            val event = correlated.optJSONObject(i) ?: continue
            val pkg = event.optString("packageName")
            val level = (0 until risks.length()).mapNotNull { risks.optJSONObject(it) }
                .firstOrNull { it.optString("packageName") == pkg }?.optString("level") ?: "CLEAR"
            if (level == "HIGH CAUTION" || dispositions.optString(pkg) != "IGNORE") visibleChanges.put(event)
        }
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
            .put("baselineChangePriorities", correlated)
            .put("baselineChangePriorityCount",correlated.length())
            .put("visibleBaselineChangePriorities", visibleChanges)
            .put("visibleBaselineChangePriorityCount", visibleChanges.length())
            .put("securityDiff", changes.optString("headline", "No comparison available"))
            .put("securityDiffDetail", changes.optString("detail", ""))
            .put("recommendedAction", if (priorities.length() > 0)
                priorities.getJSONObject(0).optString("nextStep")
                else "Save a trusted baseline and review future changes.")
            .put("priorityCount", priorities.length())
            .put("correlationNote", "Priority order combines observed severity, saved-baseline access changes, and Watch classifications. New grants or declarations are not proof of misuse.")
            .put("residualRisk", "ScoutTrace cannot prove a device is malware-free. Android may limit package visibility and some security state is not exposed to third-party apps.")
    }
}
