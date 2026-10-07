package com.cactusbyte.scouttrace

import android.content.Context
import org.json.JSONObject

class AppDispositionStore(context: Context) {
    private val prefs = context.getSharedPreferences("scouttrace_app_dispositions", Context.MODE_PRIVATE)

    fun set(packageName: String, state: String): Boolean {
        if (packageName.isBlank() || state !in setOf("TRUSTED", "WATCH", "DEFAULT")) return false
        if (state == "DEFAULT") prefs.edit().remove(packageName).apply()
        else prefs.edit().putString(packageName, state).apply()
        return true
    }

    fun all(): JSONObject {
        val out = JSONObject()
        prefs.all.forEach { (packageName, value) -> out.put(packageName, value.toString()) }
        return out
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
