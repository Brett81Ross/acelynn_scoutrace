package com.cactusbyte.scouttrace

import org.json.JSONObject

object RemediationGuide {
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
