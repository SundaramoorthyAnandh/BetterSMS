package com.bettersms.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bettersms.app.container
import com.bettersms.app.data.SmsRole
import com.bettersms.app.security.BiometricAuth
import com.bettersms.app.security.DeviceIntegrity
import com.bettersms.app.ui.glass.GlassCard
import com.bettersms.app.ui.glass.GlassDivider
import com.bettersms.app.ui.glass.GlassIconButton
import com.bettersms.app.ui.glass.SectionTitle
import com.bettersms.app.ui.theme.Glass

private data class Check(
    val icon: ImageVector,
    val title: String,
    val detail: String,
    /** true = good, false = risk, null = informational. */
    val ok: Boolean?,
    val action: (() -> Unit)? = null,
)

@Composable
fun SecurityCenterScreen(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val c = Glass.colors
    val container = remember { ctx.container() }
    val data by container.store.data.collectAsStateWithLifecycle()
    val inbox by container.repo.state.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val hardwareKey = remember { container.store.isHardwareBacked() }

    val checks = remember(data, refresh, inbox.threatsDetected) {
        val s = data.settings
        val fp = BiometricAuth.fingerprintEnrolled(ctx)
        listOf(
            Check(Icons.Rounded.Fingerprint, "Fingerprint lock", if (s.appLockEnabled) "On — BetterSMS needs your fingerprint to open" else "Off — turn it on in Settings", s.appLockEnabled, onOpenSettings),
            Check(Icons.Rounded.Lock, "Phone screen lock", if (DeviceIntegrity.isDeviceSecure(ctx)) "Your phone has a PIN, pattern or password" else "No screen lock — anyone can use this phone", DeviceIntegrity.isDeviceSecure(ctx)),
            Check(Icons.Rounded.Fingerprint, "Fingerprint enrolled", if (fp) "A fingerprint is registered on this phone" else "No fingerprint registered; PIN will be used", if (fp) true else null),
            Check(Icons.Rounded.VisibilityOff, "Screenshot protection", if (s.blockScreenshots) "Screenshots, recordings and Recents previews are blocked" else "Other apps could capture your messages on screen", s.blockScreenshots, onOpenSettings),
            Check(Icons.Rounded.NotificationsOff, "Private notifications", if (s.hideNotificationContent) "Message text is hidden in notifications" else "Lock screen always hides content; unlocked shows previews", if (s.hideNotificationContent) true else null, onOpenSettings),
            Check(Icons.Rounded.GppGood, "Scam & phishing shield", if (s.scamProtection) "${inbox.threatsDetected} suspicious messages caught" else "Off — scams won't be flagged", s.scamProtection, onOpenSettings),
            Check(Icons.Rounded.CloudOff, "No internet access", if (!DeviceIntegrity.hasInternetPermission(ctx)) "BetterSMS can't send your data anywhere" else "Unexpected: internet permission present", !DeviceIntegrity.hasInternetPermission(ctx)),
            Check(Icons.Rounded.Backup, "Cloud backup disabled", "App data is never copied to cloud backups", DeviceIntegrity.isBackupDisabled(ctx)),
            Check(Icons.Rounded.Storage, "Encrypted app storage", if (hardwareKey) "AES-256 key sealed in your phone's secure hardware" else "AES-256 key in Android Keystore", true),
            Check(Icons.Rounded.Sms, "Default SMS app", if (SmsRole.isDefault(ctx)) "Full control: blocking and OTP auto-delete work" else "Set as default for blocking and auto-delete", if (SmsRole.isDefault(ctx)) true else null),
            Check(Icons.Rounded.PhoneAndroid, "Device integrity", if (DeviceIntegrity.isLikelyRooted()) "Root detected — other apps may read your data" else "No signs of rooting", !DeviceIntegrity.isLikelyRooted()),
            Check(Icons.Rounded.Usb, "USB debugging", if (DeviceIntegrity.isAdbEnabled(ctx)) "On — a connected computer could access this phone" else "Off", if (DeviceIntegrity.isAdbEnabled(ctx)) false else true),
            Check(Icons.Rounded.DeveloperMode, "Debugger", if (DeviceIntegrity.isDebuggerAttached()) "A debugger is attached to BetterSMS" else "No debugger attached", !DeviceIntegrity.isDebuggerAttached()),
        )
    }
    val scored = checks.filter { it.ok != null }
    val good = scored.count { it.ok == true }
    val score = if (scored.isEmpty()) 0f else good.toFloat() / scored.size

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = bottom + 24.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                Spacer(Modifier.width(12.dp))
                Text("Security Center", color = c.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), strong = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                        val track = c.divider
                        val start = c.accent
                        val end = if (scored.none { it.ok == false }) c.success else if (score >= 0.7f) c.warning else c.danger
                        Canvas(Modifier.size(96.dp)) {
                            val stroke = 10.dp.toPx()
                            val inset = stroke / 2
                            val arcSize = Size(size.width - stroke, size.height - stroke)
                            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                            drawArc(
                                Brush.sweepGradient(listOf(start, end, start)),
                                -90f, 360f * score, false, Offset(inset, inset), arcSize,
                                style = Stroke(stroke, cap = StrokeCap.Round),
                            )
                        }
                        Text("${(score * 100).toInt()}", color = c.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                scored.none { it.ok == false } -> "Fully protected"
                                score >= 0.7f -> "Almost there"
                                else -> "Needs attention"
                            },
                            color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("$good of ${scored.size} protections active", color = c.textSecondary, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${data.blocked.size} senders blocked · ${inbox.threatsDetected} threats caught", color = c.textTertiary, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            SectionTitle("Checks")
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                checks.forEachIndexed { i, ch ->
                    if (i > 0) GlassDivider()
                    CheckRow(ch)
                }
            }
        }
    }
}

@Composable
private fun CheckRow(ch: Check) {
    val c = Glass.colors
    val (statusIcon, color) = when (ch.ok) {
        true -> Icons.Rounded.CheckCircle to c.success
        false -> Icons.Rounded.Warning to c.danger
        null -> Icons.Rounded.Info to c.textTertiary
    }
    com.bettersms.app.ui.glass.SettingRow(
        icon = ch.icon,
        title = ch.title,
        subtitle = ch.detail,
        iconTint = if (ch.ok == false) c.danger else c.accent,
        onClick = ch.action,
        trailing = { Icon(statusIcon, null, tint = color) },
    )
}
