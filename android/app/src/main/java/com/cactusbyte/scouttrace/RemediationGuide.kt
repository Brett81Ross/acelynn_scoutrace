package com.cactusbyte.scouttrace

import org.json.JSONObject

object RemediationGuide {
    /** Guidance is informational; do not automatically remove potentially sensitive apps. */
    fun forObservedChange(priority: String, packageName: String?): JSONObject {
        val step = when(priority) {
            "PRIORITY_REVIEW" -> "Review Android App Info, Accessibility and Device Administrator settings. If unwanted monitoring is possible, plan for personal safety before changing or removing access."
            "REVIEW_ACTIVE_ACCESS" -> "Verify the Android-reported active Accessibility or Device Administrator capability and whether you recognize it before making changes."
            "REVIEW_NEW_GRANT" -> "Check Android App Info for the newly reported sensitive permission and decide whether the app needs it."
            else -> "Review the new service or permission declaration. A declaration does not establish active special access."
        }
        return JSONObject().put("packageName", packageName ?: JSONObject.NULL)
            .put("priority", priority).put("nextStep", step)
            .put("automaticActionTaken", false)
            .put("note", "This is not proof of malware and no action was taken. If monitoring is suspected, consider personal safety before changing settings.")
    }


    fun forFinding(level: String, packageName: String?): JSONObject {
        val next = when (level) {
            "HIGH CAUTION" -> "Review the app details and security-relevant access before deciding whether to remove it."
            "ELEVATED" -> "Review the app, its permissions, install source, and whether you recognize its security-sensitive capabilities."
            "REVIEW" -> "Confirm that you recognize this app or setting and that the observed behavior is expected."
            else -> "No action is required from this observation."
        }
        return JSONObject()
            .put("packageName", packageName ?: JSONObject.NULL)
            .put("nextStep", next)
            .put("automaticActionTaken", false)
            .put("note", "ScoutTrace does not automatically uninstall apps or change Android security settings.")
    }
}
