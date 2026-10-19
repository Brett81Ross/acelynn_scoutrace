package com.cactusbyte.scouttrace

import org.json.JSONArray
import org.json.JSONObject

/** Baseline-relative triage only. No new malware verdicts or inferred change times. */
object BaselineChangeCorrelation {
    private val sensitiveGrants = setOf(
        "android.permission.CAMERA", "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION", "android.permission.READ_SMS",
        "android.permission.READ_CONTACTS", "android.permission.READ_CALL_LOG",
        "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        "android.permission.BODY_SENSORS", "android.permission.BODY_SENSORS_BACKGROUND"
    )
    private val activeNames = setOf("accessibilityEnabled", "deviceAdminActive")
    private fun entries(a: JSONArray?): Set<String> =
        (0 until (a?.length() ?: 0)).mapNotNull { i -> a?.optString(i)?.takeIf(String::isNotBlank) }.toSet()

    fun correlate(comparison: JSONObject, appRisks: JSONArray?): JSONArray {
        if (!comparison.optBoolean("exists")) return JSONArray()
        val apps = (0 until (appRisks?.length() ?: 0)).mapNotNull { i ->
            appRisks?.optJSONObject(i)?.let { app ->
                app.optString("packageName").takeIf(String::isNotBlank)?.let { it to app }
            }
        }.toMap()
        val metadata = comparison.optJSONArray("metadataChanges") ?: JSONArray()
        val results = mutableListOf<JSONObject>()
        for (i in 0 until metadata.length()) {
            val change = metadata.optJSONObject(i) ?: continue
            val pkg = change.optString("packageName").takeIf(String::isNotBlank) ?: continue
            val app = apps[pkg] ?: continue
            val active = entries(change.optJSONArray("accessEnabled")).intersect(activeNames)
            val grants = entries(change.optJSONArray("grantsAdded")).intersect(sensitiveGrants)
            val declarations = entries(change.optJSONArray("specialDeclarationsAdded"))
            if (active.isEmpty() && grants.isEmpty() && declarations.isEmpty()) continue
            val combined = app.optBoolean("accessibilityEnabled") && app.optBoolean("deviceAdminActive")
            val priority = when {
                active.isNotEmpty() && combined -> "PRIORITY_REVIEW"
                active.isNotEmpty() -> "REVIEW_ACTIVE_ACCESS"
                grants.isNotEmpty() -> "REVIEW_NEW_GRANT"
                else -> "CONTEXT_ONLY"
            }
            val explanation = when (priority) {
                "PRIORITY_REVIEW" -> "Saved baseline comparison newly observes active access in an app with Android-reported Accessibility and Device Administrator access."
                "REVIEW_ACTIVE_ACCESS" -> "Saved baseline comparison newly observes an Android-reported active privilege."
                "REVIEW_NEW_GRANT" -> "Saved baseline comparison newly observes a sensitive permission grant; a grant does not prove use."
                else -> "Saved baseline comparison found a newly declared special capability, not proof of active access."
            }
            results += JSONObject().put("packageName", pkg).put("appName", app.optString("name", pkg))
                .put("priority", priority).put("detail", explanation)
                .put("activeAccessEnabled", JSONArray(active.sorted()))
                .put("sensitiveGrantsAdded", JSONArray(grants.sorted()))
                .put("specialDeclarationsAdded", JSONArray(declarations.sorted()))
                .put("remediation", RemediationGuide.forObservedChange(priority, pkg))
                .put("observedAtIsChangeTime", false)
        }
        val ranking = listOf("PRIORITY_REVIEW", "REVIEW_ACTIVE_ACCESS", "REVIEW_NEW_GRANT", "CONTEXT_ONLY")
        return JSONArray().apply {
            results.sortedWith(compareBy<JSONObject> {
                ranking.indexOf(it.optString("priority"))
            }.thenBy { it.optString("packageName") }).forEach { put(it) }
        }
    }
}
