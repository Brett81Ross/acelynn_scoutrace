package com.cactusbyte.scouttrace

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Android Keystore backed AES-GCM storage for sensitive local investigation data. */
internal class SecureInvestigationStore(context: Context) {
    private val prefs = context.getSharedPreferences("scouttrace_security", Context.MODE_PRIVATE)
    private val alias = "scouttrace_investigation_aes_v1"
    private val keys = setOf("baseline", "timeline", "lastFingerprint")
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }
    fun getString(name: String, fallback: String? = null): String? {
        val raw = prefs.getString(name, null) ?: return fallback
        if (name !in keys) return raw
        if (raw.startsWith("gcm1:")) return try {
            val bytes = Base64.decode(raw.removePrefix("gcm1:"), Base64.NO_WRAP)
            require(bytes.size > 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) { fallback }
        // Legacy plaintext is read only to migrate immediately into encrypted storage.
        putString(name, raw)
        return raw
    }
    fun putString(name: String, value: String) {
        if (name !in keys) { prefs.edit().putString(name, value).apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(name, "gcm1:" + Base64.encodeToString(data, Base64.NO_WRAP)).commit()
    }
    fun remove(vararg names: String) {
        prefs.edit().apply { names.forEach { remove(it) } }.apply()
    }
}
