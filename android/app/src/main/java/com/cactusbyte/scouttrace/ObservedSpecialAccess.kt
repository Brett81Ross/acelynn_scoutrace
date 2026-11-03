package com.cactusbyte.scouttrace

/** Reads the shape of Android Secure Settings only; never reads message or keystroke contents. */
object ObservedSpecialAccess {
    const val SCHEMA_VERSION = 3
    private val validPackage = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")

    fun packageFromComponent(value: String?): String? {
        val raw = value?.trim() ?: return null
        if (raw.length > 1024) return null
        val slash = raw.indexOf('/')
        if (slash <= 0 || slash == raw.lastIndex || raw.indexOf('/', slash + 1) >= 0) return null
        val pkg = raw.substring(0, slash)
        val service = raw.substring(slash + 1)
        if (!validPackage.matches(pkg) || service.isBlank() || service.any { it.isWhitespace() || it == ':' }) return null
        if (!service.startsWith('.') && !service.contains('.')) return null
        return pkg
    }

    /** null means unavailable or invalid; an empty set means readable and empty. */
    fun notificationListenerPackages(raw: String?): Set<String>? {
        if (raw == null) return null
        if (raw.length > 8192) return null
        if (raw.isBlank()) return emptySet()
        if (raw.count { it == ':' } > 255) return null
        val packages = mutableSetOf<String>()
        for (part in raw.split(':')) {
            packages += packageFromComponent(part) ?: return null
        }
        return packages
    }

    fun selectedInputMethodPackage(raw: String?): String? = packageFromComponent(raw)

    data class Snapshot(val listenerPackages: Set<String>?, val selectedInputMethodPackage: String?) {
        fun listenerEnabled(pkg: String): Boolean? = listenerPackages?.contains(pkg)
        fun keyboardSelected(pkg: String): Boolean? = selectedInputMethodPackage?.let { it == pkg }
        fun listenerState(pkg: String): String = when (listenerEnabled(pkg)) {
            true -> "OS_REPORTED_ENABLED_SETTING"
            false -> "NOT_IN_REPORTED_SETTING"
            null -> "UNKNOWN"
        }
        fun keyboardState(pkg: String): String = when (keyboardSelected(pkg)) {
            true -> "OS_REPORTED_SELECTED_SETTING"
            false -> "NOT_SELECTED_IN_REPORTED_SETTING"
            null -> "UNKNOWN"
        }
    }
}
