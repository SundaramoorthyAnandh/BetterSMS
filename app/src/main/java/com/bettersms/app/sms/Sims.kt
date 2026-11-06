package com.bettersms.app.sms

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/** User's "SIM for sending" choice; real subscription ids are >= 0. */
object SimChoice {
    const val ASK = -2
    const val PHONE_DEFAULT = -1
}

data class SimInfo(
    val subId: Int,
    val slotIndex: Int,
    val carrier: String,
    val color: Int,
) {
    val label: String get() = "SIM ${slotIndex + 1}"
    val fullLabel: String get() = if (carrier.isBlank()) label else "$label · $carrier"
}

object Sims {
    fun hasPhonePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    /** Number of SIM slots that are switched on. Works without any permission. */
    fun slotCount(context: Context): Int {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return 1
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tm.activeModemCount
            } else {
                @Suppress("DEPRECATION")
                tm.phoneCount
            }
        }.getOrDefault(1)
    }

    /** Whether this phone could have two SIMs, so we may need to ask which one to use. */
    fun isMultiSimCapable(context: Context): Boolean = slotCount(context) >= 2

    /** Inserted SIMs, sorted by slot. Empty without READ_PHONE_STATE. */
    @SuppressLint("MissingPermission")
    fun active(context: Context): List<SimInfo> {
        if (!hasPhonePermission(context)) return emptyList()
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        return runCatching {
            sm.activeSubscriptionInfoList.orEmpty().map {
                SimInfo(
                    subId = it.subscriptionId,
                    slotIndex = it.simSlotIndex,
                    carrier = (it.carrierName ?: it.displayName)?.toString()?.trim().orEmpty(),
                    color = it.iconTint,
                )
            }.sortedBy { it.slotIndex }
        }.getOrDefault(emptyList())
    }

    fun defaultSmsSubId(): Int = runCatching { SubscriptionManager.getDefaultSmsSubscriptionId() }
        .getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)

    /**
     * SIM to use without asking, or null when the user must pick.
     * Returns [SimChoice.PHONE_DEFAULT] when there is only one SIM (or we can't see them).
     */
    fun resolveWithoutAsking(setting: Int, sims: List<SimInfo>): Int? {
        if (sims.size < 2) return sims.firstOrNull()?.subId ?: SimChoice.PHONE_DEFAULT
        return when {
            setting >= 0 && sims.any { it.subId == setting } -> setting
            setting == SimChoice.PHONE_DEFAULT -> defaultSmsSubId().takeIf { id -> sims.any { it.subId == id } } ?: SimChoice.PHONE_DEFAULT
            else -> null
        }
    }

    /**
     * SIM for replies that can't show a picker (notification reply, respond-via-message):
     * a fixed SIM if the user chose one, otherwise the SIM the message arrived on.
     */
    fun resolveForReply(context: Context, setting: Int, receivedSubId: Int): Int {
        val sims = active(context)
        if (setting >= 0 && sims.any { it.subId == setting }) return setting
        if (setting == SimChoice.ASK && receivedSubId >= 0) return receivedSubId
        return SimChoice.PHONE_DEFAULT
    }

    /** Subscription id an SMS broadcast or respond-via-message intent came in on, or -1. */
    fun subIdFromIntent(intent: Intent): Int =
        intent.getIntExtra("subscription", intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1))
}
