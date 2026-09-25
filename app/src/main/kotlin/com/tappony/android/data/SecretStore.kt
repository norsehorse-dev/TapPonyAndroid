package com.tappony.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.tappony.core.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Named secrets for {secret:NAME} references and auth helpers.
 *
 * One AES-256-GCM key lives in the Android Keystore and never leaves it; it
 * wraps a small file holding the secrets as JSON. EncryptedSharedPreferences is
 * deprecated, so this is the replacement with the same shape as the iOS
 * Keychain items. Excluded from backup by the manifest rules.
 */
class SecretStore(context: Context) {

    private val file = File(context.noBackupFilesDir, "secrets.bin")

    @Synchronized
    fun names(): List<String> = readAll().keys.sorted()

    @Synchronized
    fun all(): Map<String, String> = readAll()

    @Synchronized
    fun put(name: String, value: String) {
        val m = readAll().toMutableMap()
        m[name] = value
        writeAll(m)
    }

    @Synchronized
    fun remove(name: String) {
        val m = readAll().toMutableMap()
        if (m.remove(name) != null) writeAll(m)
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun readAll(): Map<String, String> {
        if (!file.exists()) return emptyMap()
        return try {
            val bytes = file.readBytes()
            val iv = bytes.copyOfRange(0, 12)
            val c = Cipher.getInstance(TRANSFORMATION)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val plain = c.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8)
            @Suppress("UNCHECKED_CAST")
            (Json.parse(plain) as Map<String, Any?>).mapValues { it.value as String }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun writeAll(m: Map<String, String>) {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(Json.write(m.toSortedMap()).toByteArray(Charsets.UTF_8))
        val tmp = File(file.parentFile, "secrets.bin.tmp")
        tmp.writeBytes(c.iv + ct)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "tappony_secrets"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        val NAME_PATTERN = Regex("[A-Za-z0-9_]+")
    }
}
