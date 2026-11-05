package com.bettersms.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.bettersms.app.container
import com.bettersms.app.data.SmsRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives SMS when BetterSMS is the default SMS app; we must store the message ourselves. */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val app = context.applicationContext
        val pending = goAsync()
        app.container().appScope.launch(Dispatchers.IO) {
            try {
                IncomingSmsHandler.handle(app, intent, writeToProvider = true)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Receives SMS when another app is the default; the system stores the message, we classify + notify. */
class SmsReceivedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (SmsRole.isDefault(context)) return // SMS_DELIVER handles it
        val app = context.applicationContext
        val pending = goAsync()
        app.container().appScope.launch(Dispatchers.IO) {
            try {
                IncomingSmsHandler.handle(app, intent, writeToProvider = false)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Required for the default SMS role. MMS download is not supported yet, so this only
 * acknowledges the push; see README "Limitations".
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
