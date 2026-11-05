package com.bettersms.app.ui.common

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.bettersms.app.classify.Category
import com.bettersms.app.classify.LinkInfo
import com.bettersms.app.classify.SenderInfo
import com.bettersms.app.data.SmsRole
import com.bettersms.app.ui.theme.Glass
import com.bettersms.app.ui.theme.color
import com.bettersms.app.ui.theme.icon
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

fun Context.findActivity(): android.app.Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

val SMS_PERMISSIONS: Array<String> = buildList {
    add(Manifest.permission.READ_SMS)
    add(Manifest.permission.RECEIVE_SMS)
    add(Manifest.permission.SEND_SMS)
    add(Manifest.permission.READ_CONTACTS)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

fun Context.hasPermission(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

/** Re-reads whether we're the default SMS app every time the screen resumes. */
@Composable
fun rememberIsDefaultSmsApp(): Boolean {
    val ctx = LocalContext.current
    var value by remember { mutableStateOf(SmsRole.isDefault(ctx)) }
    LifecycleResumeEffect(Unit) {
        value = SmsRole.isDefault(ctx)
        onPauseOrDispose { }
    }
    return value
}

@Composable
fun rememberPermissionState(permission: String): Boolean {
    val ctx = LocalContext.current
    var value by remember { mutableStateOf(ctx.hasPermission(permission)) }
    LifecycleResumeEffect(Unit) {
        value = ctx.hasPermission(permission)
        onPauseOrDispose { }
    }
    return value
}

@Composable
fun rememberRequestDefaultSms(onResult: () -> Unit = {}): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onResult() }
    return {
        try {
            launcher.launch(SmsRole.requestIntent(ctx))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "Open Settings › Apps › Default apps › SMS", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun rememberNow(periodMs: Long = 1000L): State<Long> = produceState(System.currentTimeMillis(), periodMs) {
    while (true) {
        delay(periodMs)
        value = System.currentTimeMillis()
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val dayFmt = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
private val dateFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val fullDateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val headerFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())

fun formatListTime(epochMs: Long): String {
    val zone = ZoneId.systemDefault()
    val t = Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = LocalDate.now(zone)
    val d = t.toLocalDate()
    return when {
        d == today -> timeFmt.format(t)
        ChronoUnit.DAYS.between(d, today) < 7 -> dayFmt.format(t)
        d.year == today.year -> dateFmt.format(t)
        else -> fullDateFmt.format(t)
    }
}

fun formatTime(epochMs: Long): String = timeFmt.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

fun dayOf(epochMs: Long): LocalDate = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()

fun formatDayHeader(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> headerFmt.format(day)
    }
}

fun openUrl(context: Context, url: String) {
    val fixed = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(url)) url else "https://$url"
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fixed)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
    }
}

private val avatarPalettes = listOf(
    listOf(Color(0xFF5B8CFF), Color(0xFF8B5CF6)),
    listOf(Color(0xFF22C1C3), Color(0xFF2D9CDB)),
    listOf(Color(0xFFFF8A65), Color(0xFFF06292)),
    listOf(Color(0xFF34D399), Color(0xFF059669)),
    listOf(Color(0xFFFBBF24), Color(0xFFF97316)),
    listOf(Color(0xFFA78BFA), Color(0xFFEC4899)),
)

@Composable
fun Avatar(name: String, sender: SenderInfo?, category: Category?, size: Dp = 48.dp) {
    val business = sender?.isBusiness == true
    val colors = when {
        category == Category.SPAM -> listOf(Color(0xFFFF6B6B), Color(0xFFE11D48))
        business && category != null -> listOf(category.color, category.color.copy(alpha = 0.7f))
        else -> avatarPalettes[(name.hashCode() and 0x7fffffff) % avatarPalettes.size]
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        if (business && category != null) {
            Icon(category.icon, null, tint = Color.White, modifier = Modifier.size(size * 0.48f))
        } else {
            val initials = name.split(' ', '-', '_').filter { it.isNotBlank() && it.first().isLetter() }
                .take(2).joinToString("") { it.first().uppercase() }
            if (initials.isEmpty()) {
                Icon(Category.PERSONAL.icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
            } else {
                Text(initials, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.36f).sp)
            }
        }
    }
}

/** Confirms before opening any link from an SMS, and shouts if it looks dangerous. */
@Composable
fun LinkSafetyDialog(link: LinkInfo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val c = Glass.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.dialog,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Icon(
                if (link.suspicious) Icons.Rounded.Warning else Icons.Rounded.Link,
                null,
                tint = if (link.suspicious) c.danger else c.accent,
            )
        },
        title = { Text(if (link.suspicious) "This link looks unsafe" else "Open this link?", color = c.textPrimary) },
        text = {
            Column {
                Text("You'll go to", color = c.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(link.host, color = if (link.suspicious) c.danger else c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text(link.url, color = c.textTertiary, fontSize = 12.sp, maxLines = 3)
                if (link.suspicious && link.reason != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(link.reason, color = c.danger, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Banks and government services never ask you to update KYC, share an OTP or pay through an SMS link.", color = c.textSecondary, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                openUrl(ctx, link.url)
            }) {
                Text(if (link.suspicious) "Open anyway" else "Open link", color = if (link.suspicious) c.danger else c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (link.suspicious) "Stay safe" else "Cancel", color = c.textPrimary) }
        },
    )
}

data class Choice<T>(val value: T, val label: String)

@Composable
fun <T> ChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    footer: String? = null,
) {
    val c = Glass.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.dialog,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, color = c.textPrimary) },
        text = {
            Column {
                choices.forEach { ch ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onSelect(ch.value)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (ch.value == selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            null,
                            tint = if (ch.value == selected) c.accent else c.textTertiary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(ch.label, color = c.textPrimary, fontSize = 16.sp)
                    }
                }
                if (footer != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(footer, color = c.textTertiary, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = c.accent) } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val c = Glass.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.dialog,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, color = c.textPrimary) },
        text = { Text(message, color = c.textSecondary) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(confirmLabel, color = if (destructive) c.danger else c.accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.textPrimary) } },
    )
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, action: (@Composable () -> Unit)? = null) {
    val c = Glass.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(c.glassFillStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = c.textSecondary, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

@Composable
fun LaunchedToast(message: String?, onShown: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(message) {
        if (message != null) {
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
            onShown()
        }
    }
}
