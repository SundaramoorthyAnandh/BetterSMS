package com.bettersms.app.security

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

enum class BiometricStatus { READY, NOT_ENROLLED, NO_HARDWARE, UNAVAILABLE }

/**
 * Fingerprint (with device PIN/pattern fallback) authentication.
 *
 * On Android 11+ every unlock is bound to a Keystore key that requires user authentication
 * and is invalidated when a new fingerprint is enrolled, so a hooked callback alone can't
 * unlock the app and we can warn when fingerprints change.
 */
object BiometricAuth {
    private val CHALLENGE = "bettersms-unlock".toByteArray()

    fun authenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun status(context: Context): BiometricStatus =
        when (BiometricManager.from(context).canAuthenticate(authenticators())) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            else -> BiometricStatus.UNAVAILABLE
        }

    /** True if a fingerprint (or other biometric) is enrolled, not just a PIN. */
    fun fingerprintEnrolled(context: Context): Boolean {
        val a = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG else BIOMETRIC_WEAK
        return BiometricManager.from(context).canAuthenticate(a) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun enrollIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        lock: AppLockManager?,
        onSuccess: () -> Unit,
        onError: (message: String?) -> Unit,
        onEnrollmentChanged: () -> Unit = {},
    ) {
        var cipher: Cipher? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val prepared = UnlockKey.prepare()
            cipher = prepared.cipher
            if (prepared.enrollmentChanged) onEnrollmentChanged()
        }

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                lock?.authInProgress = false
                if (cipher != null) {
                    val verified = runCatching { result.cryptoObject?.cipher?.doFinal(CHALLENGE) != null }.getOrDefault(false)
                    if (!verified) {
                        onError("Couldn't verify your identity. Please try again.")
                        return
                    }
                }
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                lock?.authInProgress = false
                val silent = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                onError(if (silent) null else errString.toString())
            }
        }

        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setAllowedAuthenticators(authenticators())
            .setConfirmationRequired(false)
            .build()

        lock?.authInProgress = true
        try {
            if (cipher != null) prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher)) else prompt.authenticate(info)
        } catch (e: Exception) {
            lock?.authInProgress = false
            // Fall back to a plain prompt if the crypto-bound one isn't supported on this device.
            if (cipher != null) {
                try {
                    lock?.authInProgress = true
                    val plain = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            lock?.authInProgress = false
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            lock?.authInProgress = false
                            onError(errString.toString())
                        }
                    })
                    plain.authenticate(info)
                } catch (e2: Exception) {
                    lock?.authInProgress = false
                    onError(e2.message ?: "Authentication unavailable")
                }
            } else {
                onError(e.message ?: "Authentication unavailable")
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private object UnlockKey {
    private const val ALIAS = "bettersms_unlock_v1"

    data class Prepared(val cipher: Cipher?, val enrollmentChanged: Boolean)

    fun prepare(): Prepared = try {
        Prepared(initCipher(), false)
    } catch (e: KeyPermanentlyInvalidatedException) {
        deleteKey()
        Prepared(runCatching { initCipher() }.getOrNull(), true)
    } catch (e: Exception) {
        Prepared(null, false)
    }

    private fun initCipher(): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keyStore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        return gen.generateKey()
    }

    private fun deleteKey() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }
}
