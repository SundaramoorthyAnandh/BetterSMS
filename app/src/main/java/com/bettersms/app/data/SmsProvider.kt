package com.bettersms.app.data

import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.util.Log

/** Whether BetterSMS holds the default SMS role (needed to write/delete messages). */
object SmsRole {
    fun isDefault(context: Context): Boolean =
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) == context.packageName }.getOrDefault(false)

    fun requestIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
}

/** Thin wrapper around the system SMS content provider. */
object SmsProvider {
    private const val TAG = "SmsProvider"

    fun insertInbox(context: Context, address: String, body: String, dateSent: Long, subId: Int, read: Boolean): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, dateSent)
            put(Telephony.Sms.READ, if (read) 1 else 0)
            put(Telephony.Sms.SEEN, if (read) 1 else 0)
            put(Telephony.Sms.TYPE, SmsType.INBOX)
            if (subId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        }
        return try {
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e(TAG, "insertInbox failed", e)
            null
        }
    }

    fun insertOutbox(context: Context, address: String, body: String): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, SmsType.OUTBOX)
        }
        return try {
            context.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e(TAG, "insertOutbox failed", e)
            null
        }
    }

    fun updateType(context: Context, uri: Uri, type: Int) {
        try {
            context.contentResolver.update(uri, ContentValues().apply { put(Telephony.Sms.TYPE, type) }, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "updateType failed", e)
        }
    }

    fun markThreadRead(context: Context, threadId: Long) {
        if (!SmsRole.isDefault(context)) return
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID}=? AND ${Telephony.Sms.READ}=0",
                arrayOf(threadId.toString()),
            )
        } catch (e: Exception) {
            Log.e(TAG, "markThreadRead failed", e)
        }
    }

    fun deleteThread(context: Context, threadId: Long): Boolean = try {
        context.contentResolver.delete(
            Telephony.Sms.CONTENT_URI,
            "${Telephony.Sms.THREAD_ID}=?",
            arrayOf(threadId.toString()),
        ) > 0
    } catch (e: Exception) {
        Log.e(TAG, "deleteThread failed", e)
        false
    }

    fun deleteMessages(context: Context, ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return try {
            ids.chunked(200).sumOf { chunk ->
                context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms._ID} IN (${chunk.joinToString(",")})",
                    null,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteMessages failed", e)
            0
        }
    }
}
