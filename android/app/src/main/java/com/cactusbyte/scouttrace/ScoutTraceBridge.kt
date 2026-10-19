package com.cactusbyte.scouttrace

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

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
        val baseline = baselineComparison(result)
        baseline.put("narrative", BaselineNarrative.summarize(baseline))
        // Verify both encrypted stores before changing the observation checkpoint.
        // A corrupt disposition or timeline must not silently advance scan history.
        val dispositions = AppDispositionStore(context).all()
        val priorTimeline = timeline()
        val fingerprint = ObservedAppFingerprint.calculate(result)
        val old = secure.getString("observationFingerprintV3", null)
        val observationsChanged = old != null && old != fingerprint
        if (observationsChanged) {
            addTimelineEvent("Observed visible-app state changed",
                result.optString("level", "REVIEW"), SecurityChangeNarrative.describe(baseline))
        }
        secure.putString("observationFingerprintV3", fingerprint)
        result.put("baseline", baseline)
        result.put("timeline", if (observationsChanged) timeline() else priorTimeline)
        result.put("dispositions", dispositions)
        result.put("narrative", InvestigationNarrative.build(result))
        return result.toString()
    }

    fun saveSecurityBaseline(): String {
        val result = DeviceSecurityScanner(context).scan()
        secure.putString("baseline", result.toString())
        check(prefs.edit().putLong("baselineAt", System.currentTimeMillis()).commit()) {
            "Baseline timestamp could not be saved"
        }
        addTimelineEvent("Security baseline saved", "CLEAR", "Current Android security state stored locally for future comparison.")
        return JSONObject().put("ok", true).put("savedAt", prefs.getLong("baselineAt", 0)).toString()
    }

    fun clearSecurityHistory(): Boolean {
        val activity = context as? MainActivity ?: return false
        if (!activity.confirmDestructiveAction(
                "Delete ScoutTrace security history?",
                "Permanently erase the local encrypted baseline, observation timeline, and scan fingerprint on this device? Saved app classifications will be kept."
            )) return false
        secure.remove("timeline", "baseline", "lastFingerprint", "observationFingerprintV2", "observationFingerprintV3", "baselineAt")
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
        val activity = context as? MainActivity ?: return false
        if (!activity.confirmDestructiveAction(
                "Delete saved app classifications?",
                "Permanently erase every Trust, Watch, and Ignore choice on this device? Your baseline and security timeline will be kept."
            )) return false
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
        out.append("Other installer identifiers observed: ").append(counts.optInt("otherInstallers")).append("\n")
        out.append("Apps with unavailable installer identity: ").append(counts.optInt("unknownInstallers")).append("\n\n")
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

    /** Android activities and permission/settings flows launch only from the UI thread. */
    private fun launch(intent: Intent): Boolean {
        val activity = context as? MainActivity ?: return false
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            return try {
                if (activity.isFinishing || activity.isDestroyed) return false
                activity.startActivity(intent)
                true
            } catch (_: Exception) { false }
        }
        val result = java.util.concurrent.atomic.AtomicBoolean(false)
        val stillRequested = java.util.concurrent.atomic.AtomicBoolean(true)
        val done = java.util.concurrent.CountDownLatch(1)
        activity.runOnUiThread {
            try {
                if (stillRequested.compareAndSet(true, false) &&
                    !activity.isFinishing && !activity.isDestroyed) {
                    activity.startActivity(intent)
                    result.set(true)
                }
            } catch (_: Exception) {
                result.set(false)
            } finally {
                done.countDown()
            }
        }
        val completed = try {
            done.await(10, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!completed) stillRequested.set(false)
        return completed && result.get()
    }

    private fun baselineComparison(current: JSONObject): JSONObject {
        val raw = secure.getString("baseline", null)
            ?: return JSONObject().put("exists", false)
        return try {
            BaselineComparator.compare(JSONObject(raw), current, prefs.getLong("baselineAt", 0))
        } catch (e: Exception) {
            // A failed comparison is not evidence that the saved baseline disappeared.
            throw IllegalStateException("Saved baseline could not be compared; refusing to hide or overwrite it", e)
        }
    }

    private fun addTimelineEvent(title: String, level: String, detail: String) {
        val a = try { JSONArray(secure.getString("timeline", "[]")) } catch (e: Exception) {
            throw IllegalStateException("Security timeline is unreadable; refusing to overwrite it", e)
        }
        val next = JSONArray().put(JSONObject().put("time", System.currentTimeMillis()).put("title", title).put("level", level).put("detail", detail))
        for (i in 0 until minOf(a.length(), 49)) next.put(a.get(i))
        secure.putString("timeline", next.toString())
    }

    private fun timeline(): JSONArray = try { JSONArray(secure.getString("timeline", "[]")) } catch (e: Exception) {
        throw IllegalStateException("Security timeline is unreadable; refusing to hide the error", e)
    }
}
