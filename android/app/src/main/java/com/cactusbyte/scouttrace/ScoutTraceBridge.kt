package com.cactusbyte.scouttrace

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class ScoutTraceBridge(private val context: Context) {
    private val prefs = context.getSharedPreferences("scouttrace_security", Context.MODE_PRIVATE)
    private val secure = SecureInvestigationStore(context)

    fun openApkPicker(): Boolean {
        val activity = context as? MainActivity ?: return false
        activity.requestApkInspection()
        return true
    }

    fun isNativeSecurityAvailable(): Boolean = true
    fun getPlatformInfo(): String = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"

    fun runSecurityScan(): String {
        val result = DeviceSecurityScanner(context).scan()
        val fingerprint = fingerprint(result)
        val old = secure.getString("lastFingerprint", null)
        if (old != null && old != fingerprint) addTimelineEvent("Security state changed", result.optString("level", "REVIEW"), result.optString("summary"))
        secure.putString("lastFingerprint", fingerprint)
        val baseline = baselineComparison(result)
        baseline.put("narrative", BaselineNarrative.summarize(baseline))
        result.put("baseline", baseline)
        result.put("timeline", timeline())
        result.put("dispositions", AppDispositionStore(context).all())
        result.put("narrative", InvestigationNarrative.build(result))
        return result.toString()
    }

    fun saveSecurityBaseline(): String {
        val result = DeviceSecurityScanner(context).scan()
        secure.putString("baseline", result.toString())
        prefs.edit().putLong("baselineAt", System.currentTimeMillis()).apply()
        addTimelineEvent("Security baseline saved", "CLEAR", "Current Android security state stored locally for future comparison.")
        return JSONObject().put("ok", true).put("savedAt", prefs.getLong("baselineAt", 0)).toString()
    }

    fun clearSecurityHistory(): Boolean {
        secure.remove("timeline", "baseline", "lastFingerprint", "baselineAt")
        prefs.edit().remove("baselineAt").apply()
        return true
    }

    fun openAppSettings(packageName: String): Boolean = launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))

    fun requestUninstall(packageName: String): Boolean = launch(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))

    fun openAccessibilitySettings(): Boolean = launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openSecuritySettings(): Boolean = launch(Intent(Settings.ACTION_SECURITY_SETTINGS))

    fun setAppDisposition(packageName: String, state: String): Boolean =
        AppDispositionStore(context).set(packageName, state)

    fun getAppDispositions(): String =
        AppDispositionStore(context).all().toString()

    fun clearAppDispositions(): Boolean {
        AppDispositionStore(context).clear()
        return true
    }

    fun exportSecurityReport(redacted: Boolean = false): String {
        val r = DeviceSecurityScanner(context).scan()
        val counts = r.optJSONObject("counts") ?: JSONObject()
        val findings = r.optJSONArray("findings") ?: JSONArray()
        val out = StringBuilder()
        out.append("Acelynn's ScoutTrace Security Report\n")
        out.append(if (redacted) "Sharing mode: redacted\n" else "Sharing mode: full local report\n")
        out.append("Status: ").append(r.optString("level")).append("\n")
        out.append("Platform: ").append(r.optString("platform")).append("\n")
        out.append("Security patch: ").append(r.optString("securityPatch")).append("\n")
        out.append("Packages inspected: ").append(counts.optInt("appsScanned")).append("\n")
        out.append("Findings: ").append(counts.optInt("findings")).append("\n")
        out.append("Sideload indicators: ").append(counts.optInt("sideloaded")).append("\n\n")
        for (i in 0 until findings.length()) {
            val item = findings.optJSONObject(i) ?: continue
            out.append("[").append(item.optString("level")).append("] ")
                .append(if (redacted) "App indicator " + (i + 1) else item.optString("title")).append(": ")
                .append(if (redacted) "Review this indicator locally in ScoutTrace; app identity and evidence withheld from shared report." else item.optString("detail")).append("\n")
        }
        out.append("\nScoutTrace reports observable security indicators; it does not certify a device malware-free.")
        return out.toString()
    }

    fun shareSecurityReport(): Boolean {
        val text = exportSecurityReport(redacted = true)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Acelynn's ScoutTrace Security Report")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return launch(Intent.createChooser(intent, "Share ScoutTrace report"))
    }

    fun privacySummary(): String = PrivacyCenter.summary().toString()

    private fun launch(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Exception) { false }

    private fun baselineComparison(current: JSONObject): JSONObject {
        val raw = secure.getString("baseline", null) ?: return JSONObject().put("exists", false)
        return try {
            val base = JSONObject(raw)
            val nowPkgs = packageSet(current.optJSONArray("appRisks"))
            val basePkgs = packageSet(base.optJSONArray("appRisks"))
            val baseRisk = riskMap(base.optJSONArray("appRisks"))
            val nowRisk = riskMap(current.optJSONArray("appRisks"))
            val changed = JSONArray()
            (baseRisk.keys intersect nowRisk.keys).sorted().forEach { pkg ->
                val before = baseRisk[pkg]!!
                val after = nowRisk[pkg]!!
                if (before.first != after.first || before.second != after.second) {
                    changed.put(JSONObject()
                        .put("packageName", pkg)
                        .put("fromLevel", before.first).put("toLevel", after.first)
                        .put("fromScore", before.second).put("toScore", after.second))
                }
            }
            val metadataChanges = JSONArray()
            val oldApps = appMetadata(base.optJSONArray("appRisks"))
            val newApps = appMetadata(current.optJSONArray("appRisks"))
            (oldApps.keys intersect newApps.keys).sorted().forEach { pkg ->
                val before = oldApps.getValue(pkg)
                val after = newApps.getValue(pkg)
                val fields = listOf("versionCode", "installer", "requestedPermissions", "accessibilityEnabled", "deviceAdminActive")
                val changes = JSONArray()
                fields.forEach { field ->
                    if (before.opt(field)?.toString() != after.opt(field)?.toString()) changes.put(field)
                }
                if (changes.length() > 0) metadataChanges.put(JSONObject().put("packageName", pkg).put("fields", changes))
            }
            JSONObject().put("exists", true).put("savedAt", prefs.getLong("baselineAt", 0))
                .put("newApps", JSONArray((nowPkgs - basePkgs).sorted()))
                .put("removedApps", JSONArray((basePkgs - nowPkgs).sorted()))
                .put("riskChanges", changed)
                .put("metadataChanges", metadataChanges)
                .put("levelChanged", base.optString("level") != current.optString("level"))
        } catch (_: Exception) { JSONObject().put("exists", false) }
    }

    private fun appMetadata(a: JSONArray?): Map<String, JSONObject> {
        if (a == null) return emptyMap()
        return (0 until a.length()).mapNotNull { i ->
            val item = a.optJSONObject(i) ?: return@mapNotNull null
            val pkg = item.optString("packageName").takeIf(String::isNotBlank) ?: return@mapNotNull null
            pkg to item
        }.toMap()
    }

    private fun packageSet(a: JSONArray?): Set<String> {
        if (a == null) return emptySet()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("packageName")?.takeIf(String::isNotBlank) }.toSet()
    }

    private fun riskMap(a: JSONArray?): Map<String, Pair<String, Int>> {
        if (a == null) return emptyMap()
        return (0 until a.length()).mapNotNull { i ->
            val item = a.optJSONObject(i) ?: return@mapNotNull null
            val pkg = item.optString("packageName").takeIf(String::isNotBlank) ?: return@mapNotNull null
            pkg to (item.optString("level") to item.optInt("score"))
        }.toMap()
    }

    private fun fingerprint(result: JSONObject): String {
        val risks = result.optJSONArray("appRisks") ?: JSONArray()
        val stable = (0 until risks.length()).mapNotNull { i ->
            risks.optJSONObject(i)?.let { item ->
                listOf(
                    item.optString("packageName"),
                    item.optString("level"),
                    item.optInt("score").toString(),
                    item.optString("versionCode"),
                    item.optString("installer"),
                    item.optString("requestedPermissions"),
                    item.optString("accessibilityEnabled"),
                    item.optString("deviceAdminActive")
                ).joinToString("|")
            }
        }.sorted().joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256").digest(stable.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun addTimelineEvent(title: String, level: String, detail: String) {
        val a = try { JSONArray(secure.getString("timeline", "[]")) } catch (_: Exception) { JSONArray() }
        val next = JSONArray().put(JSONObject().put("time", System.currentTimeMillis()).put("title", title).put("level", level).put("detail", detail))
        for (i in 0 until minOf(a.length(), 49)) next.put(a.get(i))
        secure.putString("timeline", next.toString())
    }

    private fun timeline(): JSONArray = try { JSONArray(secure.getString("timeline", "[]")) } catch (_: Exception) { JSONArray() }
}
