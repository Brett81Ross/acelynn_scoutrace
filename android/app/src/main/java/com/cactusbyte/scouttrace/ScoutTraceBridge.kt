package com.cactusbyte.scouttrace

import android.content.Context
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class ScoutTraceBridge(private val context: Context) {
    private val prefs = context.getSharedPreferences("scouttrace_security", Context.MODE_PRIVATE)

    @JavascriptInterface fun isNativeSecurityAvailable(): Boolean = true
    @JavascriptInterface fun getPlatformInfo(): String = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"

    @JavascriptInterface fun runSecurityScan(): String {
        val result = DeviceSecurityScanner(context).scan()
        val fingerprint = fingerprint(result)
        val old = prefs.getString("lastFingerprint", null)
        if (old != null && old != fingerprint) addTimelineEvent("Security state changed", result.optString("level", "REVIEW"), result.optString("summary"))
        prefs.edit().putString("lastFingerprint", fingerprint).apply()
        result.put("baseline", baselineComparison(result))
        result.put("timeline", timeline())
        return result.toString()
    }

    @JavascriptInterface fun saveSecurityBaseline(): String {
        val result = DeviceSecurityScanner(context).scan()
        prefs.edit().putString("baseline", result.toString()).putLong("baselineAt", System.currentTimeMillis()).apply()
        addTimelineEvent("Security baseline saved", "CLEAR", "Current Android security state stored locally for future comparison.")
        return JSONObject().put("ok", true).put("savedAt", prefs.getLong("baselineAt", 0)).toString()
    }

    @JavascriptInterface fun clearSecurityHistory(): Boolean {
        prefs.edit().remove("timeline").remove("baseline").remove("baselineAt").remove("lastFingerprint").apply()
        return true
    }

    private fun baselineComparison(current: JSONObject): JSONObject {
        val raw = prefs.getString("baseline", null) ?: return JSONObject().put("exists", false)
        return try {
            val base = JSONObject(raw)
            val nowPkgs = packageSet(current.optJSONArray("appRisks"))
            val basePkgs = packageSet(base.optJSONArray("appRisks"))
            JSONObject().put("exists", true).put("savedAt", prefs.getLong("baselineAt", 0))
                .put("newApps", JSONArray((nowPkgs - basePkgs).sorted()))
                .put("removedApps", JSONArray((basePkgs - nowPkgs).sorted()))
                .put("levelChanged", base.optString("level") != current.optString("level"))
        } catch (_: Exception) { JSONObject().put("exists", false) }
    }

    private fun packageSet(a: JSONArray?): Set<String> {
        if (a == null) return emptySet()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("packageName")?.takeIf(String::isNotBlank) }.toSet()
    }

    private fun fingerprint(result: JSONObject): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(result.optJSONArray("appRisks").toString().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun addTimelineEvent(title: String, level: String, detail: String) {
        val a = try { JSONArray(prefs.getString("timeline", "[]")) } catch (_: Exception) { JSONArray() }
        val next = JSONArray().put(JSONObject().put("time", System.currentTimeMillis()).put("title", title).put("level", level).put("detail", detail))
        for (i in 0 until minOf(a.length(), 49)) next.put(a.get(i))
        prefs.edit().putString("timeline", next.toString()).apply()
    }

    private fun timeline(): JSONArray = try { JSONArray(prefs.getString("timeline", "[]")) } catch (_: Exception) { JSONArray() }
}
