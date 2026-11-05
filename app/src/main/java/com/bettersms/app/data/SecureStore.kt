package com.bettersms.app.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.bettersms.app.classify.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

data class AppSettings(
    val onboardingDone: Boolean = false,
    val appLockEnabled: Boolean = false,
    /** 0 = lock as soon as the app leaves the screen. */
    val lockTimeoutSec: Int = 0,
    val blockScreenshots: Boolean = true,
    val hideNotificationContent: Boolean = false,
    val otpAutoCopy: Boolean = false,
    /** 0 = never clear. */
    val clipboardClearSec: Int = 60,
    /** 0 = never delete. Requires default SMS app. */
    val otpAutoDeleteHours: Int = 0,
    val scamProtection: Boolean = true,
    val linkWarnings: Boolean = true,
    val notifySpam: Boolean = false,
    /** 0 = system, 1 = light, 2 = dark. */
    val themeMode: Int = 0,
)

data class StoreData(
    val settings: AppSettings = AppSettings(),
    /** Sender keys (see SenderAnalyzer.key). */
    val blocked: Set<String> = emptySet(),
    /** Sender key -> user chosen category. */
    val overrides: Map<String, Category> = emptyMap(),
    /** "t:<threadId>" or sender key -> epoch ms when last read inside BetterSMS. */
    val readMarks: Map<String, Long> = emptyMap(),
)

/**
 * Small encrypted key-value store. The whole document is AES-256-GCM encrypted with a
 * non-exportable key that lives in the Android Keystore (hardware-backed on most phones),
 * and the file is kept in no-backup storage.
 */
class SecureStore(context: Context) {
    private val file = File(context.noBackupFilesDir, "secure_store.bin")
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "secure-store").apply { isDaemon = true } }
    private val _data = MutableStateFlow(readOrDefault())
    val data: StateFlow<StoreData> = _data.asStateFlow()

    val settings: AppSettings get() = _data.value.settings

    @Synchronized
    fun update(block: (StoreData) -> StoreData) {
        val current = _data.value
        val next = block(current)
        if (next == current) return
        _data.value = next
        writer.execute { write(next) }
    }

    fun updateSettings(block: (AppSettings) -> AppSettings) = update { it.copy(settings = block(it.settings)) }

    /** Deletes everything BetterSMS stores (not the SMS themselves). */
    @Synchronized
    fun wipe() {
        _data.value = StoreData()
        writer.execute {
            file.delete()
            KeystoreCipher.deleteKey()
        }
    }

    fun isHardwareBacked(): Boolean = KeystoreCipher.isHardwareBacked()

    private fun readOrDefault(): StoreData {
        if (!file.exists()) return StoreData()
        return try {
            val json = String(KeystoreCipher.decrypt(file.readBytes()), Charsets.UTF_8)
            fromJson(JSONObject(json))
        } catch (e: Exception) {
            Log.w(TAG, "Secure store unreadable, resetting", e)
            file.delete()
            StoreData()
        }
    }

    private fun write(data: StoreData) {
        try {
            val bytes = KeystoreCipher.encrypt(toJson(data).toString().toByteArray(Charsets.UTF_8))
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist secure store", e)
        }
    }

    private fun toJson(d: StoreData) = JSONObject().apply {
        val s = d.settings
        put("settings", JSONObject().apply {
            put("onboardingDone", s.onboardingDone)
            put("appLockEnabled", s.appLockEnabled)
            put("lockTimeoutSec", s.lockTimeoutSec)
            put("blockScreenshots", s.blockScreenshots)
            put("hideNotificationContent", s.hideNotificationContent)
            put("otpAutoCopy", s.otpAutoCopy)
            put("clipboardClearSec", s.clipboardClearSec)
            put("otpAutoDeleteHours", s.otpAutoDeleteHours)
            put("scamProtection", s.scamProtection)
            put("linkWarnings", s.linkWarnings)
            put("notifySpam", s.notifySpam)
            put("themeMode", s.themeMode)
        })
        put("blocked", JSONArray(d.blocked.toList()))
        put("overrides", JSONObject().apply { d.overrides.forEach { (k, v) -> put(k, v.name) } })
        put("readMarks", JSONObject().apply { d.readMarks.forEach { (k, v) -> put(k, v) } })
    }

    private fun fromJson(o: JSONObject): StoreData {
        val d = AppSettings()
        val s = o.optJSONObject("settings") ?: JSONObject()
        val settings = AppSettings(
            onboardingDone = s.optBoolean("onboardingDone", d.onboardingDone),
            appLockEnabled = s.optBoolean("appLockEnabled", d.appLockEnabled),
            lockTimeoutSec = s.optInt("lockTimeoutSec", d.lockTimeoutSec),
            blockScreenshots = s.optBoolean("blockScreenshots", d.blockScreenshots),
            hideNotificationContent = s.optBoolean("hideNotificationContent", d.hideNotificationContent),
            otpAutoCopy = s.optBoolean("otpAutoCopy", d.otpAutoCopy),
            clipboardClearSec = s.optInt("clipboardClearSec", d.clipboardClearSec),
            otpAutoDeleteHours = s.optInt("otpAutoDeleteHours", d.otpAutoDeleteHours),
            scamProtection = s.optBoolean("scamProtection", d.scamProtection),
            linkWarnings = s.optBoolean("linkWarnings", d.linkWarnings),
            notifySpam = s.optBoolean("notifySpam", d.notifySpam),
            themeMode = s.optInt("themeMode", d.themeMode),
        )
        val blocked = buildSet {
            val arr = o.optJSONArray("blocked") ?: JSONArray()
            for (i in 0 until arr.length()) add(arr.getString(i))
        }
        val overrides = buildMap {
            val obj = o.optJSONObject("overrides") ?: JSONObject()
            obj.keys().forEach { k ->
                runCatching { Category.valueOf(obj.getString(k)) }.getOrNull()?.let { put(k, it) }
            }
        }
        val readMarks = buildMap {
            val obj = o.optJSONObject("readMarks") ?: JSONObject()
            obj.keys().forEach { k -> put(k, obj.optLong(k)) }
        }
        return StoreData(settings, blocked, overrides, readMarks)
    }

    private companion object {
        const val TAG = "SecureStore"
    }
}

internal object KeystoreCipher {
    private const val ALIAS = "bettersms_store_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val VERSION: Byte = 1

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized
    private fun key(): SecretKey {
        (keyStore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain)
        return byteArrayOf(VERSION, iv.size.toByte()) + iv + ct
    }

    fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size > 2 && blob[0] == VERSION) { "Unknown store format" }
        val ivLen = blob[1].toInt()
        val iv = blob.copyOfRange(2, 2 + ivLen)
        val ct = blob.copyOfRange(2 + ivLen, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    fun deleteKey() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    fun isHardwareBacked(): Boolean = runCatching {
        val k = key()
        val info = SecretKeyFactory.getInstance(k.algorithm, "AndroidKeyStore").getKeySpec(k, KeyInfo::class.java) as KeyInfo
        if (Build.VERSION.SDK_INT >= 31) {
            info.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
        } else {
            @Suppress("DEPRECATION")
            info.isInsideSecureHardware
        }
    }.getOrDefault(false)
}
