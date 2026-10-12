package com.cactusbyte.scouttrace

import android.content.Context
import org.json.JSONObject

/** Sensitive app classifications are Keystore-encrypted and never leave the device implicitly. */
class AppDispositionStore(context: Context) {
    private val secure = SecureInvestigationStore(context)
    private val legacy = context.getSharedPreferences("scouttrace_app_dispositions", Context.MODE_PRIVATE)
    private val key = "appDispositions"
    private val states = setOf("TRUSTED", "WATCH", "IGNORE", "DEFAULT")
    private val packagePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")

    private fun read(): JSONObject {
        val encrypted = secure.getString(key, null)
        if (encrypted != null) return try { JSONObject(encrypted) } catch (_: Exception) { JSONObject() }
        val old = legacy.all
        if (old.isEmpty()) return JSONObject()
        val migrated = JSONObject()
        old.forEach { (pkg, value) ->
            if (packagePattern.matches(pkg) && value.toString() in states && value.toString() != "DEFAULT") {
                migrated.put(pkg, value.toString())
            }
        }
        // Only remove plaintext after a successful encrypted write.
        secure.putString(key, migrated.toString())
        check(legacy.edit().clear().commit()) { "Could not clear legacy plaintext app classifications" }
        return migrated
    }

    fun set(packageName: String, state: String): Boolean {
        if (!packagePattern.matches(packageName) || state !in states) return false
        val updated = read()
        if (state == "DEFAULT") updated.remove(packageName)
        else updated.put(packageName, state)
        secure.putString(key, updated.toString())
        return true
    }

    fun all(): JSONObject = read()

    fun clear() {
        secure.putString(key, "{}")
        legacy.edit().clear().commit()
    }
}
