package com.cactusbyte.scouttrace

import org.json.JSONObject

/**
 * Separates verified privileged state from manifest declarations and installer metadata.
 * Correlation means independently observed active privileges; it is not a malware verdict.
 */
object EvidenceModel {
    fun assess(
        sideloaded: Boolean,
        accessibility: Boolean,
        admin: Boolean,
        overlay: Boolean,
        installPackages: Boolean,
        debuggable: Boolean,
        notificationListener: Boolean = false
    ): JSONObject {
        // 'sideloaded' means an installer other than a recognized store ID was observed;
        // it does not prove how the APK was delivered.
        val verifiedPrivilegeCount = listOf(accessibility, admin).count { it }
        val enabledSettingCount = if (notificationListener) 1 else 0
        val declaredCapabilityCount = listOf(overlay, installPackages).count { it }
        val contextCount = listOf(sideloaded, debuggable).count { it }
        val indicatorCount = verifiedPrivilegeCount + enabledSettingCount + declaredCapabilityCount + contextCount
        val correlated = accessibility && admin
        val evidence = when {
            correlated -> "HIGH"
            verifiedPrivilegeCount >= 1 && indicatorCount >= 2 -> "MEDIUM"
            indicatorCount >= 2 -> "LOW"
            indicatorCount == 1 -> "LOW"
            else -> "NONE"
        }
        val context = when {
            correlated -> "STRONG"
            indicatorCount >= 2 -> "MODERATE"
            indicatorCount == 1 -> "WEAK"
            else -> "NONE"
        }
        val confidence = when {
            correlated -> "HIGH"
            verifiedPrivilegeCount >= 1 && indicatorCount >= 2 -> "MEDIUM"
            indicatorCount >= 1 -> "LOW"
            else -> "INSUFFICIENT"
        }
        val explanation = when {
            correlated -> "Android reports both an enabled Accessibility service and an active Device Administrator. Verify that this combination is expected; it is not proof of malware."
            verifiedPrivilegeCount > 0 -> "Android reports an enabled Accessibility service or active Device Administrator. This does not prove that any capability was used."
            indicatorCount > 0 -> "Signals include declarations, installer metadata, build flags or an enabled notification-listener setting. The setting is not confirmation of a connected service or notification access."
            else -> "No listed indicators were observable. Limited Android visibility does not prove an app is safe."
        }
        return JSONObject()
            .put("evidenceStrength", evidence)
            .put("contextStrength", context)
            .put("confidence", confidence)
            .put("correlated", correlated)
            .put("indicatorCount", indicatorCount)
            .put("verifiedPrivilegeCount", verifiedPrivilegeCount)
            .put("enabledSettingCount", enabledSettingCount)
            .put("declaredCapabilityCount", declaredCapabilityCount)
            .put("contextIndicatorCount", contextCount)
            .put("explanation", explanation)
    }
}
