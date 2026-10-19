package com.cactusbyte.scouttrace

/**
 * Deterministic triage policy. A signal may justify review, but no combination
 * of metadata or unverified manifest declarations alone can imply active intrusion.
 * Increment VERSION when these classification rules change.
 */
object AppRiskPolicy {
    const val VERSION = 2

    data class Signals(
        val otherInstaller: Boolean = false,
        val accessibilityEnabled: Boolean = false,
        val deviceAdminActive: Boolean = false,
        val overlayDeclared: Boolean = false,
        val installRequestsDeclared: Boolean = false,
        val debuggable: Boolean = false
    )

    data class Decision(
        val level: String,
        val score: Int,
        val rule: String,
        val activePrivilegeCount: Int,
        val declaredCapabilityCount: Int,
        val contextIndicatorCount: Int,
        val reasons: List<String>
    )

    fun evaluate(signals: Signals): Decision {
        val reasons = mutableListOf<String>()
        var score = 0
        if (signals.otherInstaller) {
            score += 2
            reasons += "reported installer differs from recognized store IDs; not proof of sideloading"
        }
        if (signals.accessibilityEnabled) {
            score += 4
            reasons += "Android reports an enabled Accessibility service"
        }
        if (signals.deviceAdminActive) {
            score += 4
            reasons += "Android reports an active Device Administrator"
        }
        if (signals.overlayDeclared) {
            score += 1
            reasons += "declares overlay permission; current access is not verified"
        }
        if (signals.installRequestsDeclared) {
            score += 1
            reasons += "declares package-install request permission; current access is not verified"
        }
        if (signals.debuggable) {
            score += 1
            reasons += "app is marked debuggable"
        }
        val active = listOf(signals.accessibilityEnabled, signals.deviceAdminActive).count { it }
        val declared = listOf(signals.overlayDeclared, signals.installRequestsDeclared).count { it }
        val context = listOf(signals.otherInstaller, signals.debuggable).count { it }
        val supporting = declared + context
        val rule = when {
            active == 2 -> "TWO_ACTIVE_PRIVILEGES"
            active == 1 && supporting >= 1 -> "ACTIVE_PRIVILEGE_WITH_CONTEXT"
            active == 1 -> "SINGLE_ACTIVE_PRIVILEGE"
            declared > 0 && context > 0 -> "DECLARATIONS_AND_CONTEXT_ONLY"
            declared > 0 -> "DECLARATIONS_ONLY"
            context > 0 -> "INSTALLER_OR_BUILD_CONTEXT_ONLY"
            else -> "NO_LISTED_INDICATORS"
        }
        val level = when (rule) {
            "TWO_ACTIVE_PRIVILEGES" -> "HIGH CAUTION"
            "ACTIVE_PRIVILEGE_WITH_CONTEXT" -> "ELEVATED"
            "NO_LISTED_INDICATORS" -> "CLEAR"
            else -> "REVIEW"
        }
        return Decision(level, score, rule, active, declared, context, reasons)
    }
}
