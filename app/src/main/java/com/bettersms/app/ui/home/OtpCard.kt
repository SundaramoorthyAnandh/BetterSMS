package com.bettersms.app.ui.home

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bettersms.app.classify.Category
import com.bettersms.app.container
import com.bettersms.app.data.SmsMessage
import com.bettersms.app.security.ClipboardGuard
import com.bettersms.app.ui.common.formatListTime
import com.bettersms.app.ui.glass.GlassCard
import com.bettersms.app.ui.glass.GradientButton
import com.bettersms.app.ui.glass.Pill
import com.bettersms.app.ui.theme.Glass
import com.bettersms.app.ui.theme.color

fun copyOtp(context: android.content.Context, code: String) {
    val c = context.container()
    ClipboardGuard.copySensitive(context, code, c.store.settings.clipboardClearSec)
    val clear = c.store.settings.clipboardClearSec
    if (Build.VERSION.SDK_INT < 33) {
        Toast.makeText(context, if (clear > 0) "OTP copied · clipboard clears in ${clear}s" else "OTP copied", Toast.LENGTH_SHORT).show()
    }
}

/** Big, glanceable OTP with live countdown and one-tap copy. */
@Composable
fun OtpCard(message: SmsMessage, senderName: String, now: Long, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val otp = message.otp ?: return
    val ctx = LocalContext.current
    val c = Glass.colors
    val validMs = otp.effectiveValidityMinutes * 60_000L
    val remaining = message.date + validMs - now
    val active = remaining > 0
    val accent = Category.OTP.color

    GlassCard(modifier = modifier.fillMaxWidth(), strong = active, tint = if (active) accent else null, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(Brush.linearGradient(listOf(accent, c.accent)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Key, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(otp.service ?: senderName, color = c.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                Text(
                    if (active) "Expires in ${formatRemaining(remaining)}" else "Expired · ${formatListTime(message.date)}",
                    color = if (active) accent else c.textTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (message.threat.isThreat) {
                Pill("Suspicious", c.danger, icon = Icons.Rounded.Warning)
            } else if (active) {
                Pill("Active", c.success)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                otp.code.chunked(if (otp.code.length >= 6) 3 else 2).joinToString(" "),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Code " + otp.code.toCharArray().joinToString(" ") },
                color = if (active) c.textPrimary else c.textTertiary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
            )
            GradientButton(
                "Copy",
                onClick = { copyOtp(ctx, otp.code) },
                icon = Icons.Rounded.ContentCopy,
                height = 44.dp,
            )
        }
        if (active) {
            Spacer(Modifier.height(12.dp))
            val fraction = (remaining.toFloat() / validMs).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(c.divider, RoundedCornerShape(2.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(c.accent, accent)), RoundedCornerShape(2.dp)),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Shield, null, tint = c.textTertiary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (message.threat.isThreat) "This message looks suspicious — don't share this code with anyone."
                else "Never share this code. No bank or company will call to ask for it.",
                color = if (message.threat.isThreat) c.danger else c.textTertiary,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

private fun formatRemaining(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
