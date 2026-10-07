package com.cactusbyte.scouttrace

import android.os.Build
import org.json.JSONObject

object ScanCoverage {
    fun packageCoverage(visibleCount: Int): JSONObject =
        JSONObject()
            .put("scope", if (Build.VERSION.SDK_INT >= 30) "VISIBLE_PACKAGES" else "LEGACY_VISIBLE_PACKAGES")
            .put("visiblePackageCount", visibleCount)
            .put("completeInventory", false)
            .put("confidence", "INSUFFICIENT_FOR_COMPLETE_INVENTORY")
            .put("explanation", "Android package visibility can limit which apps ScoutTrace can inspect. Findings apply only to packages visible to ScoutTrace.")
            .put("nextStep", "Use APK Inspector for a user-selected APK when deeper inspection is needed.")
}
