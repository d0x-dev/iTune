package com.darkxvenom.itune.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Self-repairing AndroidKeyStore AES-256-GCM credential cipher wrapping SharedPreferences.
 * Falls back cleanly to plain SharedPreferences only if the hardware KeyStore is unusable.
 */
internal object EncryptedAuthPrefs {
    private const val TAG = "EncryptedAuthPrefs"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "itune_ytm_auth_aes_v1"
    private const val GCM_TAG_BITS = 128
    private const val PREFIX_ENC = "enc_v1:"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun open(context: Context): SharedPreferences =
        prefs ?: synchronized(this) {
            prefs ?: context.applicationContext
                .getSharedPreferences("itune_auth_store", Context.MODE_PRIVATE)
                .also { prefs = it }
        }

    fun putEncrypted(context: Context, key: String, value: String?) {
        val p = open(context)
        if (value == null) {
            p.edit().remove(key).apply()
            return
        }
        val stored = encryptString(value) ?: value
        p.edit().putString(key, stored).apply()
    }

    fun getDecrypted(context: Context, key: String): String? {
        val raw = open(context).getString(key, null) ?: return null
        if (!raw.startsWith(PREFIX_ENC)) return raw
        val decoded = decryptString(raw.removePrefix(PREFIX_ENC))
        if (decoded == null) {
            Log.w(TAG, "Removing undecryptable key=$key after KeyStore rotation/restore")
            open(context).edit().remove(key).apply()
        }
        return decoded
    }

    private fun encryptString(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val dataB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        "$PREFIX_ENC$ivB64:$dataB64"
    }.onFailure {
        Log.w(TAG, "KeyStore encrypt failed, using fallback: ${it.message}")
    }.getOrNull()

    private fun decryptString(payload: String): String? = runCatching {
        val sep = payload.indexOf(':')
        if (sep <= 0) return null
        val iv = Base64.decode(payload.substring(0, sep), Base64.NO_WRAP)
        val data = Base64.decode(payload.substring(sep + 1), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
