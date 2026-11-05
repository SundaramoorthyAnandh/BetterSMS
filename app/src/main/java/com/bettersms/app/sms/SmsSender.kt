package com.bettersms.app.sms

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

    fun send(context: Context, address: String, body: String): Boolean {
        val to = address.trim()
        if (to.isEmpty() || body.isBlank()) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val manager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val parts = manager.divideMessage(body)
        // The default SMS app must record its own sent messages; otherwise the system does it.
        val uri = if (SmsRole.isDefault(context)) SmsProvider.insertOutbox(context, to, body) else null

        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val sentIntents = ArrayList<PendingIntent>(parts.size)
        parts.forEachIndexed { i, _ ->
            val intent = Intent(context, SmsSentReceiver::class.java)
                .setAction(SmsSentReceiver.ACTION_SENT)
                .putExtra(SmsSentReceiver.EXTRA_URI, uri?.toString())
                .putExtra(SmsSentReceiver.EXTRA_ADDRESS, to)
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
            true
        } catch (e: Exception) {
            Log.e(TAG, "Send failed", e)
            uri?.let { SmsProvider.updateType(context, it, SmsType.FAILED) }
            false
        }
    }
}
