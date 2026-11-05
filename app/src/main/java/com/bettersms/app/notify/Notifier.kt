package com.bettersms.app.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.bettersms.app.MainActivity
import com.bettersms.app.R
import com.bettersms.app.classify.Category
import com.bettersms.app.classify.Classification
import com.bettersms.app.classify.SenderAnalyzer
import com.bettersms.app.classify.ThreatLevel
import com.bettersms.app.data.AppSettings

object Notifier {
    const val CH_MESSAGES = "messages"
    const val CH_OTP = "otp"
    const val CH_SPAM = "spam"
    const val CH_STATUS = "status"

    private const val COLOR = 0xFF415FFF.toInt()

    fun createChannels(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CH_MESSAGES, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName("Messages")
                    .setDescription("Personal, transaction and update messages")
                    .build(),
                NotificationChannelCompat.Builder(CH_OTP, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName("OTP codes")
                    .setDescription("One-time passwords with a quick Copy button")
                    .build(),
                NotificationChannelCompat.Builder(CH_SPAM, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName("Suspicious messages")
                    .setDescription("Messages that look like scams or phishing")
                    .build(),
                NotificationChannelCompat.Builder(CH_STATUS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("Sending status")
                    .setDescription("Alerts when a message fails to send")
                    .build(),
            ),
        )
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notificationIdFor(address: String): Int = ("n:" + SenderAnalyzer.keyOf(address)).hashCode()

    private fun openIntent(context: Context, address: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_THREAD)
            .putExtra(MainActivity.EXTRA_ADDRESS, address)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionIntent(context: Context, action: String, address: String, notifId: Int, mutable: Boolean = false, extras: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(NotificationActionReceiver.EXTRA_ADDRESS, address)
            .putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId)
            .apply(extras)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE
            else if (mutable) 0 else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, (action + notifId).hashCode(), intent, flags)
    }

    fun notifyIncoming(
        context: Context,
        address: String,
        body: String,
        displayName: String,
        cls: Classification,
        settings: AppSettings,
    ) {
        if (!canNotify(context)) return
        val sender = SenderAnalyzer.analyze(address)
        val hide = settings.hideNotificationContent
        val notifId = notificationIdFor(address)
        val otp = cls.otp

        val builder: NotificationCompat.Builder
        when {
            cls.category == Category.SPAM -> {
                if (!settings.notifySpam) return
                builder = NotificationCompat.Builder(context, CH_SPAM)
                    .setSmallIcon(R.drawable.ic_stat_message)
                    .setContentTitle("Suspicious message filtered")
                    .setContentText(if (hide) "Tap to review in BetterSMS" else "From $displayName · moved to Spam")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
            }
            otp != null -> {
                val service = otp.service ?: displayName
                builder = NotificationCompat.Builder(context, CH_OTP)
                    .setSmallIcon(R.drawable.ic_stat_otp)
                    .setContentTitle(if (hide) "OTP received" else "${otp.code}  ·  $service")
                    .setContentText(
                        if (hide) "Unlock to view" else buildString {
                            append("One-time password")
                            otp.validityMinutes?.let { append(" · valid $it min") }
                            append(" · never share it")
                        },
                    )
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setTimeoutAfter(otp.effectiveValidityMinutes * 60_000L)
                    .setOnlyAlertOnce(false)
                    .addAction(
                        NotificationCompat.Action.Builder(
                            R.drawable.ic_copy,
                            if (hide) "Copy OTP" else "Copy ${otp.code}",
                            actionIntent(context, NotificationActionReceiver.ACTION_COPY_OTP, address, notifId) {
                                putExtra(NotificationActionReceiver.EXTRA_CODE, otp.code)
                            },
                        ).setAuthenticationRequired(true).build(),
                    )
                if (cls.threat.isThreat && !hide) {
                    builder.setContentText("⚠ This message looks suspicious. Don't share this code.")
                }
            }
            else -> {
                val warn = cls.threat.level == ThreatLevel.SUSPICIOUS
                val text = if (hide) "New message" else (if (warn) "⚠ Possible scam · " else "") + body
                builder = NotificationCompat.Builder(context, CH_MESSAGES)
                    .setSmallIcon(R.drawable.ic_stat_message)
                    .setContentTitle(if (hide) "BetterSMS" else displayName)
                    .setContentText(text)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                if (!hide) builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
                builder.addAction(
                    NotificationCompat.Action.Builder(
                        0, "Mark read",
                        actionIntent(context, NotificationActionReceiver.ACTION_MARK_READ, address, notifId),
                    ).build(),
                )
                if (sender.canReply) {
                    val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY).setLabel("Reply").build()
                    builder.addAction(
                        NotificationCompat.Action.Builder(
                            0, "Reply",
                            actionIntent(context, NotificationActionReceiver.ACTION_REPLY, address, notifId, mutable = true),
                        ).addRemoteInput(remoteInput).setAuthenticationRequired(true).setAllowGeneratedReplies(false).build(),
                    )
                }
            }
        }

        val publicVersion = NotificationCompat.Builder(context, builder.build().channelId ?: CH_MESSAGES)
            .setSmallIcon(if (otp != null) R.drawable.ic_stat_otp else R.drawable.ic_stat_message)
            .setContentTitle(if (otp != null) "OTP received" else "New message")
            .setContentText("Unlock to view")
            .setColor(COLOR)
            .build()

        builder
            .setColor(COLOR)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(openIntent(context, address, notifId))

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun notifySendFailed(context: Context, address: String) {
        if (!canNotify(context)) return
        val id = ("fail:" + address).hashCode()
        val n = NotificationCompat.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle("Message not sent")
            .setContentText("Couldn't send to $address. Check signal and try again.")
            .setColor(COLOR)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, address, id))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    fun cancel(context: Context, notifId: Int) = NotificationManagerCompat.from(context).cancel(notifId)
}
