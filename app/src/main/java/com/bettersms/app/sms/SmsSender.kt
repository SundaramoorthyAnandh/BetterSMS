package com.bettersms.app.sms

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.bettersms.app.data.SmsProvider
import com.bettersms.app.data.SmsRole
import com.bettersms.app.data.SmsType
import java.util.concurrent.atomic.AtomicInteger

object SmsSender {
    private const val TAG = "SmsSender"
    private val requestCodes = AtomicInteger((System.currentTimeMillis() % 100_000).toInt())

    sealed interface Result {
        /** Handed to the radio; [providerUri] is our row in the SMS store when we're the default app. */
        data class Queued(val providerUri: Uri?) : Result
        data class Failed(val reason: String) : Result
    }

    private fun managerFor(context: Context, subId: Int): SmsManager {
        val base: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java) ?: @Suppress("DEPRECATION") SmsManager.getDefault()
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        if (subId < 0) return base
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            base.createForSubscriptionId(subId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subId)
        }
    }

    /**
     * Sends [body] to [address] on SIM [subId] (or the phone's default SIM when negative).
     * [localId] links the delivery callback back to BetterSMS's sent log.
     */
    fun send(context: Context, address: String, body: String, subId: Int, localId: Long, date: Long): Result {
        val to = address.trim()
        if (to.isEmpty() || body.isBlank()) return Result.Failed("Empty message")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return Result.Failed("SMS permission missing")
        }
        val manager = try {
            managerFor(context, subId)
        } catch (e: Exception) {
            Log.e(TAG, "No SmsManager for sub $subId", e)
            return Result.Failed("SIM not available")
        }
        val parts = try {
            manager.divideMessage(body)
        } catch (e: Exception) {
            // No SMS service (no SIM, airplane mode on some devices, tablets).
            Log.e(TAG, "SMS unavailable", e)
            return Result.Failed("SMS isn't available right now")
        }
        // The default SMS app must record its own sent messages; otherwise the system should.
        val uri = if (SmsRole.isDefault(context)) {
            SmsProvider.insertOutgoing(context, to, body, subId, SmsType.OUTBOX, date)
        } else {
            null
        }

        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val sentIntents = ArrayList<PendingIntent>(parts.size)
        parts.forEachIndexed { i, _ ->
            val intent = Intent(context, SmsSentReceiver::class.java)
                .setAction(SmsSentReceiver.ACTION_SENT)
                .putExtra(SmsSentReceiver.EXTRA_URI, uri?.toString())
                .putExtra(SmsSentReceiver.EXTRA_ADDRESS, to)
                .putExtra(SmsSentReceiver.EXTRA_LOCAL_ID, localId)
                .putExtra(SmsSentReceiver.EXTRA_LAST, i == parts.lastIndex)
            sentIntents += PendingIntent.getBroadcast(
                context,
                requestCodes.incrementAndGet(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
        }
        return try {
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(to, null, parts, sentIntents, null)
            } else {
                manager.sendTextMessage(to, null, body, sentIntents.first(), null)
            }
            Result.Queued(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Send failed", e)
            uri?.let { SmsProvider.updateType(context, it, SmsType.FAILED) }
            Result.Failed(e.message ?: "Send failed")
        }
    }
}
