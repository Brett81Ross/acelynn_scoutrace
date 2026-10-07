package com.cactusbyte.scouttrace

import org.json.JSONArray
import org.json.JSONObject

object PrivacyCenter {
    fun summary(): JSONObject = JSONObject()
        .put("analysisLocation", "ON_DEVICE")
        .put("uploadsInstalledAppInventory", false)
        .put("uploadsScanHistory", false)
        .put("externalReputationLookupEnabled", false)
        .put("storedLocally", JSONArray(listOf(
            "security baseline",
            "security timeline",
            "last scan fingerprint",
            "app review classifications"
        )))
        .put("userControls", JSONArray(listOf(
            "clear local security history",
            "clear app classifications",
            "review scan coverage"
        )))
        .put("statement", "ScoutTrace v2.1 native security analysis runs on this Android device. External reputation lookup is not enabled in this build.")
}
