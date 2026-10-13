package com.cactusbyte.scouttrace

import org.json.JSONObject

/** Timeline timestamps are observation times, not inferred install or permission-change times. */
object SecurityChangeNarrative {
    fun describe(baselineComparison: JSONObject): String {
        val common = "The Android-visible app snapshot differs from the previous ScoutTrace scan. " +
            "This timestamp records observation, not when the underlying change occurred."
        if (!baselineComparison.optBoolean("exists")) return common +
            " Save a trusted baseline to compare named fields in future scans."
        val narrative = BaselineNarrative.summarize(baselineComparison)
        return if (narrative.optBoolean("changed")) common +
            " Compared with the saved baseline: " + narrative.optString("headline") +
            ". Package visibility changes can affect this comparison."
        else common + " No difference is visible in the currently compared baseline fields."
    }
}
