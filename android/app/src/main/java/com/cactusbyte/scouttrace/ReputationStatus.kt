package com.cactusbyte.scouttrace

import org.json.JSONObject

object ReputationStatus {
    fun current(): JSONObject = JSONObject()
        .put("enabled", false)
        .put("mode", "NOT_CONFIGURED")
        .put("localVerdict", false)
        .put("externalLookupPerformed", false)
        .put("message", "External threat reputation is not enabled in this build. ScoutTrace will not imply that an app or APK was checked against an external threat database.")
}
