package com.cactusbyte.scouttrace

import org.json.JSONObject

object EvidenceModel {
    fun assess(
        sideloaded: Boolean,
        accessibility: Boolean,
        admin: Boolean,
        overlay: Boolean,
        installPackages: Boolean,
        debuggable: Boolean
    ): JSONObject {
        val independent = listOf(sideloaded, accessibility, admin, overlay, installPackages, debuggable).count { it }
        val correlated = accessibility && (overlay || installPackages || sideloaded)
        val evidence = when {
            correlated || independent >= 3 -> "HIGH"
            independent == 2 -> "MEDIUM"
            independent == 1 -> "LOW"
            else -> "NONE"
        }
        val context = when {
            correlated -> "STRONG"
            independent >= 2 -> "MODERATE"
            independent == 1 -> "WEAK"
            else -> "NONE"
        }
        val confidence = when {
            correlated -> "HIGH"
            independent >= 2 -> "MEDIUM"
            independent == 1 -> "LOW"
            else -> "INSUFFICIENT"
        }
        return JSONObject()
            .put("evidenceStrength", evidence)
            .put("contextStrength", context)
            .put("confidence", confidence)
            .put("correlated", correlated)
            .put("indicatorCount", independent)
            .put("explanation", if (correlated)
                "Multiple independent Android security indicators occur together."
            else
                "Confidence reflects the number of independent observable indicators; a single indicator is not treated as proof of malicious behavior.")
    }
}
