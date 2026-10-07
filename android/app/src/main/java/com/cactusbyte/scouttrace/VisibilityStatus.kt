package com.cactusbyte.scouttrace

import android.content.Context
import android.os.Build
import org.json.JSONObject

class VisibilityStatus(private val context: Context) {
    fun describe(visibleCount: Int): JSONObject = JSONObject()
        .put("mode", if (Build.VERSION.SDK_INT >= 30) "ANDROID_LIMITED_VISIBILITY" else "LEGACY_VISIBILITY")
        .put("visiblePackageCount", visibleCount)
        .put("completeInventoryGuaranteed", false)
        .put("message", "ScoutTrace analyzes packages Android makes visible to this app. This count must not be presented as a guaranteed complete device inventory.")
        .put("apkInspectionAvailable", true)
}
