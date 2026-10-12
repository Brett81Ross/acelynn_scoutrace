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
    private val maxApkBytes = 256L * 1024L * 1024L
    fun inspect(uri: Uri): JSONObject {
        val temp = File.createTempFile("scouttrace_", ".apk", context.cacheDir)
        try {
            require(uri.scheme == "content") { "Only user-selected documents can be inspected." }
            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        totalBytes += count
                        require(totalBytes <= maxApkBytes) { "APK exceeds the 256 MB inspection limit." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageArchiveInfo(temp.absolutePath, PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES)
                ?: return JSONObject().put("ok", false).put("error", "Android could not parse the selected APK.")
            val permissions = info.requestedPermissions?.toList().orEmpty()
            return JSONObject()
                .put("ok", true)
                .put("sha256", digest.digest().joinToString("") { "%02x".format(it) })
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
