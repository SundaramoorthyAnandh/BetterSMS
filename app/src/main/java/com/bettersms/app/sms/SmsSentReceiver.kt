package com.bettersms.app.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bettersms.app.container
import com.bettersms.app.notify.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SENT) return
        val ok = resultCode == Activity.RESULT_OK
        val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        val last = intent.getBooleanExtra(EXTRA_LAST, true)
        val localId = intent.getLongExtra(EXTRA_LOCAL_ID, 0L)
        val app = context.applicationContext
        val c = app.container()
        val pending = goAsync()
        c.appScope.launch(Dispatchers.IO) {
            try {
                c.repo.onSendResult(localId, uri, ok, last)
                if (!ok) Notifier.notifySendFailed(app, intent.getStringExtra(EXTRA_ADDRESS).orEmpty())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SENT = "com.bettersms.app.SMS_SENT"
        const val EXTRA_URI = "uri"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_LAST = "last"
        const val EXTRA_LOCAL_ID = "local_id"
    }
}
