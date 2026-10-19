package com.cactusbyte.scouttrace

import org.json.JSONArray
import org.json.JSONObject

object BaselineNarrative {
    fun summarize(comparison: JSONObject): JSONObject {
        if (!comparison.optBoolean("exists")) return JSONObject().put("changed", false).put("headline", "No trusted baseline saved").put("detail", "Save a baseline to compare future ScoutTrace investigations.")
        val added = comparison.optJSONArray("newApps") ?: JSONArray()
        val removed = comparison.optJSONArray("removedApps") ?: JSONArray()
        val riskChanges = comparison.optJSONArray("riskChanges") ?: JSONArray()
        val metadata = comparison.optJSONArray("metadataChanges") ?: JSONArray()
        val modelChanged = comparison.optBoolean("assessmentModelChanged")
        val permissionSchemaChanged = comparison.optBoolean("permissionEvidenceSchemaChanged")
        val enabledPrivileges = (0 until metadata.length()).sumOf {
            metadata.optJSONObject(it)?.optJSONArray("accessEnabled")?.length() ?: 0
        }
        val total = added.length() + removed.length() + riskChanges.length() + metadata.length()
        val headline = when {
            enabledPrivileges > 0 -> "Previously unobserved active app access needs review"
            total == 0 && (modelChanged || permissionSchemaChanged) -> "ScoutTrace evidence schema updated; comparable fields checked"
            total == 0 -> "No material baseline changes detected"
            metadata.length() > 0 -> metadata.length().toString() + " app configuration change(s)"
            riskChanges.length() > 0 -> riskChanges.length().toString() + " app risk classification change(s)"
            added.length() > 0 && removed.length() > 0 -> "App inventory changed"
            added.length() > 0 -> added.length().toString() + " newly visible app(s)"
            else -> removed.length().toString() + " previously visible app(s) no longer seen"
        }
        return JSONObject().put("changed", total > 0).put("changeCount", total).put("headline", headline)
            .put("newVisibleApps", added.length()).put("noLongerVisibleApps", removed.length()).put("riskChanges", riskChanges.length()).put("metadataChanges", metadata.length()).put("newlyEnabledPrivileges", enabledPrivileges)
            .put("assessmentModelChanged", modelChanged)
            .put("permissionEvidenceSchemaChanged", permissionSchemaChanged)
            .put("detail", "Baseline differences are observations for review, not the time a permission changed. Android package visibility affects which apps ScoutTrace sees." +
                (if (modelChanged) " Assessment policy changed since this baseline; older risk scores were not compared." else "") +
                (if (permissionSchemaChanged) " Newly tracked permissions were not compared against unavailable old fields." else ""))
    }
}
