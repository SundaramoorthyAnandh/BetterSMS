package com.bettersms.app.sms

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.telephony.TelephonyManager
import com.bettersms.app.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** "Respond via message" from the incoming-call screen. Required for the default SMS role. */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val recipients = intent.data?.let(::recipientsOf).orEmpty()
            if (!text.isNullOrBlank() && recipients.isNotEmpty()) {
                val app = applicationContext
                app.container().appScope.launch(Dispatchers.IO) {
                    recipients.forEach { SmsSender.send(app, it, text) }
                    stopSelf(startId)
                }
                return START_NOT_STICKY
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        fun recipientsOf(uri: Uri): List<String> =
            Uri.decode(uri.schemeSpecificPart.orEmpty())
                .removePrefix("//")
                .substringBefore('?')
                .split(',', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
    }
}
