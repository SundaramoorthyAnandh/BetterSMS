package com.bettersms.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.bettersms.app.container
import com.bettersms.app.security.ClipboardGuard
import com.bettersms.app.sms.Sims
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val c = app.container()
        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        when (intent.action) {
            ACTION_COPY_OTP -> {
                val code = intent.getStringExtra(EXTRA_CODE) ?: return
                ClipboardGuard.copySensitive(app, code, c.store.settings.clipboardClearSec)
                // Android 13+ shows its own clipboard confirmation.
                if (Build.VERSION.SDK_INT < 33) Toast.makeText(app, "OTP copied", Toast.LENGTH_SHORT).show()
                c.repo.markReadByAddress(address)
                Notifier.cancel(app, notifId)
            }
            ACTION_MARK_READ -> {
                c.repo.markReadByAddress(address)
                Notifier.cancel(app, notifId)
            }
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()
                Notifier.cancel(app, notifId)
                if (!text.isNullOrBlank()) {
                    val pending = goAsync()
                    c.appScope.launch(Dispatchers.IO) {
                        try {
                            val received = intent.getIntExtra(EXTRA_SUB_ID, -1)
                            val sim = Sims.resolveForReply(app, c.store.settings.sendSim, received)
                            c.repo.send(address, text, sim)
                            c.repo.markReadByAddress(address)
                        } finally {
                            pending.finish()
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_COPY_OTP = "com.bettersms.app.COPY_OTP"
        const val ACTION_MARK_READ = "com.bettersms.app.MARK_READ"
        const val ACTION_REPLY = "com.bettersms.app.REPLY"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_CODE = "code"
        const val EXTRA_NOTIF_ID = "notif_id"
        const val EXTRA_SUB_ID = "sub_id"
        const val KEY_REPLY = "reply_text"
    }
}
