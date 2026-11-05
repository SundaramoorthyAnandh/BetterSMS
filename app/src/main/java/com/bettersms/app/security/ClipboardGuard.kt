package com.bettersms.app.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock

/**
 * Copies OTPs as "sensitive" (hidden from the Android 13+ clipboard preview and keyboard
 * suggestions) and wipes the clipboard again after a timeout.
 */
object ClipboardGuard {
    private val handler = Handler(Looper.getMainLooper())
    private val clearToken = Any()

    fun copySensitive(context: Context, text: String, clearAfterSec: Int, label: String = "OTP") {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply {
            if (Build.VERSION.SDK_INT >= 33) {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            } else {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        handler.post {
            runCatching { cm.setPrimaryClip(clip) }
            handler.removeCallbacksAndMessages(clearToken)
            if (clearAfterSec > 0) {
                handler.postAtTime(
                    {
                        runCatching {
                            if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip()
                            else cm.setPrimaryClip(ClipData.newPlainText("", ""))
                        }
                    },
                    clearToken,
                    SystemClock.uptimeMillis() + clearAfterSec * 1000L,
                )
            }
        }
    }

    fun copyPlain(context: Context, text: String, label: String = "Text") {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
