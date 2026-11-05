package com.bettersms.app.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bettersms.app.container
import com.bettersms.app.data.SmsProvider
import com.bettersms.app.data.SmsType
import com.bettersms.app.notify.Notifier

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SENT) return
        val ok = resultCode == Activity.RESULT_OK
        val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        val last = intent.getBooleanExtra(EXTRA_LAST, true)
        if (uri != null) {
            when {
                !ok -> SmsProvider.updateType(context, uri, SmsType.FAILED)
                last -> SmsProvider.updateType(context, uri, SmsType.SENT)
            }
        }
        if (!ok) Notifier.notifySendFailed(context, intent.getStringExtra(EXTRA_ADDRESS).orEmpty())
        context.container().repo.requestRefresh()
    }

    companion object {
        const val ACTION_SENT = "com.bettersms.app.SMS_SENT"
        const val EXTRA_URI = "uri"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_LAST = "last"
    }
}
