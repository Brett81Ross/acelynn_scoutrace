package com.cactusbyte.scouttrace

import org.json.JSONArray
import org.json.JSONObject

object BaselineNarrative {
    fun summarize(comparison: JSONObject): JSONObject {
        if (!comparison.optBoolean("exists")) return JSONObject().put("changed", false).put("headline", "No trusted baseline saved").put("detail", "Save a baseline to compare future ScoutTrace investigations.")
        val added = comparison.optJSONArray("newApps") ?: JSONArray()
        val removed = comparison.optJSONArray("removedApps") ?: JSONArray()
        val riskChanges = comparison.optJSONArray("riskChanges") ?: JSONArray()
        val total = added.length() + removed.length() + riskChanges.length()
        val headline = when {
            total == 0 -> "No material baseline changes detected"
            riskChanges.length() > 0 -> riskChanges.length().toString() + " app risk classification change(s)"
            added.length() > 0 && removed.length() > 0 -> "App inventory changed"
            added.length() > 0 -> added.length().toString() + " newly visible app(s)"
            else -> removed.length().toString() + " previously visible app(s) no longer seen"
        }
        return JSONObject().put("changed", total > 0).put("changeCount", total).put("headline", headline)
            .put("newVisibleApps", added.length()).put("noLongerVisibleApps", removed.length()).put("riskChanges", riskChanges.length())
            .put("detail", "Baseline changes are observations for review. Android package visibility can also affect which apps ScoutTrace can see.")
    }
}
