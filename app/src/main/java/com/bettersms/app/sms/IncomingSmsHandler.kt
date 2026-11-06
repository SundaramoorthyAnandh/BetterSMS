package com.bettersms.app.sms

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.bettersms.app.classify.Category
import com.bettersms.app.classify.SenderAnalyzer
import com.bettersms.app.container
import com.bettersms.app.data.SmsProvider
import com.bettersms.app.notify.Notifier
import com.bettersms.app.security.ClipboardGuard

/** Shared path for SMS_DELIVER (default app) and SMS_RECEIVED (non-default app). */
object IncomingSmsHandler {
    private const val TAG = "IncomingSms"

    fun handle(context: Context, intent: Intent, writeToProvider: Boolean) {
        val parts = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Bad SMS intent", e)
            null
        } ?: return
        val c = context.container()
        val subId = Sims.subIdFromIntent(intent)

        parts.filterNotNull()
            .groupBy { it.displayOriginatingAddress ?: it.originatingAddress ?: "" }
            .forEach { (address, msgs) ->
                if (address.isBlank()) return@forEach
                val body = msgs.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }
                val sentAt = msgs.first().timestampMillis
                val settings = c.store.settings
                val sender = SenderAnalyzer.analyze(address)
                val blocked = c.repo.isBlocked(address)

                if (writeToProvider) {
                    // Blocked senders are stored as read so they never raise a badge.
                    SmsProvider.insertInbox(context, address, body, sentAt, subId, read = blocked)
                }
                if (blocked) return@forEach

                val cls = c.repo.classifyIncoming(address, body)
                val name = c.repo.contactName(address) ?: sender.displayName

                val otp = cls.otp
                if (otp != null && settings.otpAutoCopy && cls.category != Category.SPAM && !cls.threat.isThreat) {
                    ClipboardGuard.copySensitive(context, otp.code, settings.clipboardClearSec)
                }
                Notifier.notifyIncoming(context, address, body, name, cls, settings, subId)
            }
        c.repo.requestRefresh()
        c.repo.cleanupOldOtps()
    }
}
