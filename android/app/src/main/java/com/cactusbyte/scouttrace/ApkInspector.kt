package com.cactusbyte.scouttrace

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class ApkInspector(private val context: Context) {
    fun inspect(uri: Uri): JSONObject {
        require(uri.scheme == "content") { "Only user-selected documents can be inspected." }
        val temp = File.createTempFile("scouttrace_", ".apk", context.cacheDir)
        try {
            val copied = context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "The selected document cannot be opened." }
                FileOutputStream(temp).use { output ->
                    ApkStreamCopy.copy(input, output)
                }
            }
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageArchiveInfo(temp.absolutePath, PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES)
                ?: return JSONObject().put("ok", false).put("error", "Android could not parse the selected APK.")
            val permissions = info.requestedPermissions?.toList().orEmpty()
            return JSONObject()
                .put("ok", true)
                .put("sha256", copied.sha256)
                .put("sizeBytes", copied.bytes)
                .put("packageName", info.packageName)
                .put("versionName", info.versionName ?: "unknown")
                .put("versionCode", if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong())
                .put("minSdk", info.applicationInfo?.minSdkVersion ?: -1)
                .put("targetSdk", info.applicationInfo?.targetSdkVersion ?: -1)
                .put("signingCertificateSha256", JSONArray((if (android.os.Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners?.toList() else null)?.map { cert ->
                    MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()).joinToString("") { "%02x".format(it) }
                }.orEmpty()))
                .put("requestedPermissions", JSONArray(permissions))
                .put("note", "Local static APK metadata inspection only.")
        } finally {
            temp.delete()
        }
    }
}
