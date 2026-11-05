package com.bettersms.app.ui.settings

import android.widget.Toast
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bettersms.app.BuildConfig
import com.bettersms.app.container
import com.bettersms.app.data.AppSettings
import com.bettersms.app.security.BiometricAuth
import com.bettersms.app.security.BiometricStatus
import com.bettersms.app.ui.common.Choice
import com.bettersms.app.ui.common.ChoiceDialog
import com.bettersms.app.ui.common.ConfirmDialog
import com.bettersms.app.ui.common.findActivity
import com.bettersms.app.ui.common.rememberIsDefaultSmsApp
import com.bettersms.app.ui.common.rememberRequestDefaultSms
import com.bettersms.app.ui.glass.GlassCard
import com.bettersms.app.ui.glass.GlassDivider
import com.bettersms.app.ui.glass.GlassIconButton
import com.bettersms.app.ui.glass.GlassSwitch
import com.bettersms.app.ui.glass.SectionTitle
import com.bettersms.app.ui.glass.SettingRow
import com.bettersms.app.ui.theme.Glass

private enum class Picker { LOCK_TIMEOUT, CLIPBOARD, OTP_DELETE, THEME }

@Composable
fun SettingsScreen(onBack: () -> Unit, onSecurity: () -> Unit, onBlocked: () -> Unit) {
    val ctx = LocalContext.current
    val c = Glass.colors
    val container = remember { ctx.container() }
    val data by container.store.data.collectAsStateWithLifecycle()
    val s = data.settings
    val isDefault = rememberIsDefaultSmsApp()
    val requestDefault = rememberRequestDefaultSms { container.repo.start() }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }

    fun update(block: (AppSettings) -> AppSettings) = container.store.updateSettings(block)

    fun withAuth(title: String, action: () -> Unit) {
        val activity = ctx.findActivity() as? FragmentActivity ?: return
        if (BiometricAuth.status(ctx) != BiometricStatus.READY) {
            Toast.makeText(ctx, "Set up a fingerprint or screen lock on your phone first", Toast.LENGTH_LONG).show()
            runCatching { ctx.startActivity(BiometricAuth.enrollIntent()) }
            return
        }
        BiometricAuth.authenticate(
            activity, title, "Confirm it's you", container.lock,
            onSuccess = action,
            onError = { msg -> if (msg != null) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() },
        )
    }

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
                Text("Settings", color = c.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        item {
            SectionTitle("Security")
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                SettingRow(
                    Icons.Rounded.Fingerprint, "Fingerprint lock",
                    if (s.appLockEnabled) "Required to open BetterSMS" else "Anyone holding your phone can read your SMS",
                ) {
                    GlassSwitch(s.appLockEnabled) { on ->
                        withAuth(if (on) "Enable fingerprint lock" else "Disable fingerprint lock") {
                            update { it.copy(appLockEnabled = on) }
                            if (!on) container.lock.onLockDisabled()
                        }
                    }
                }
                GlassDivider()
                SettingRow(Icons.Rounded.Timer, "Auto-lock", lockTimeoutLabel(s.lockTimeoutSec), onClick = { picker = Picker.LOCK_TIMEOUT })
                GlassDivider()
                SettingRow(Icons.Rounded.VisibilityOff, "Block screenshots", "Also hides the app preview in Recents and stops screen recording") {
                    GlassSwitch(s.blockScreenshots) { on -> update { it.copy(blockScreenshots = on) } }
                }
                GlassDivider()
                SettingRow(Icons.Rounded.NotificationsOff, "Private notifications", "Hide sender and message text in notifications") {
                    GlassSwitch(s.hideNotificationContent) { on -> update { it.copy(hideNotificationContent = on) } }
                }
                if (s.appLockEnabled) {
                    GlassDivider()
                    SettingRow(Icons.Rounded.Lock, "Lock now", null, onClick = { container.lock.lockNow() })
                }
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
            SectionTitle("OTP assistant")
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                SettingRow(Icons.Rounded.Key, "Auto-copy OTPs", "Copy codes the moment they arrive (never for suspicious messages)") {
                    GlassSwitch(s.otpAutoCopy) { on -> update { it.copy(otpAutoCopy = on) } }
                }
                GlassDivider()
                SettingRow(Icons.Rounded.ContentPaste, "Clear clipboard", clipboardLabel(s.clipboardClearSec), onClick = { picker = Picker.CLIPBOARD })
                GlassDivider()
                SettingRow(
                    Icons.Rounded.AutoDelete, "Auto-delete old OTPs",
                    otpDeleteLabel(s.otpAutoDeleteHours) + if (!isDefault && s.otpAutoDeleteHours > 0) " · needs default SMS app" else "",
                    onClick = { picker = Picker.OTP_DELETE },
                )
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
            SectionTitle("Protection")
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                SettingRow(Icons.Rounded.GppGood, "Scam & phishing shield", "Detect fake KYC, lottery, job scams and dangerous links", iconTint = c.success) {
                    GlassSwitch(s.scamProtection) { on -> update { it.copy(scamProtection = on) } }
                }
                GlassDivider()
                SettingRow(Icons.Rounded.Link, "Confirm before opening links", "Shows the real website first. Risky links always ask.") {
                    GlassSwitch(s.linkWarnings) { on -> update { it.copy(linkWarnings = on) } }
                }
                GlassDivider()
                SettingRow(Icons.Rounded.Report, "Notify for spam", "Quietly tell me when a scam is filtered") {
                    GlassSwitch(s.notifySpam) { on -> update { it.copy(notifySpam = on) } }
                }
                GlassDivider()
                SettingRow(
                    Icons.Rounded.Block, "Blocked senders", "${data.blocked.size} blocked", iconTint = c.danger, onClick = onBlocked,
                    trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textTertiary) },
                )
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
            SectionTitle("General")
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                SettingRow(
                    Icons.Rounded.Sms, "Default SMS app",
                    if (isDefault) "BetterSMS is your SMS app" else "Tap to make BetterSMS your SMS app",
                    onClick = if (isDefault) null else requestDefault,
                )
                GlassDivider()
                SettingRow(Icons.Rounded.DarkMode, "Theme", themeLabel(s.themeMode), onClick = { picker = Picker.THEME })
                GlassDivider()
                SettingRow(
                    Icons.Rounded.Security, "Security Center", "See how protected you are",
                    onClick = onSecurity,
                    trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textTertiary) },
                )
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
            SectionTitle("Data")
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                SettingRow(
                    Icons.Rounded.DeleteForever, "Erase BetterSMS data",
                    "Resets settings, block list and categories. Your SMS stay on the phone.",
                    iconTint = c.danger,
                    onClick = { confirmWipe = true },
                )
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudOff, null, tint = c.textTertiary)
                    Spacer(Modifier.width(6.dp))
                    Text("Works 100% offline · no internet permission", color = c.textTertiary, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text("BetterSMS ${BuildConfig.VERSION_NAME}", color = c.textTertiary, fontSize = 12.sp)
            }
        }
    }

    when (picker) {
        Picker.LOCK_TIMEOUT -> ChoiceDialog(
            "Auto-lock",
            listOf(Choice(0, "Immediately"), Choice(30, "After 30 seconds"), Choice(60, "After 1 minute"), Choice(300, "After 5 minutes")),
            s.lockTimeoutSec,
            onSelect = { v -> update { it.copy(lockTimeoutSec = v) } },
            onDismiss = { picker = null },
        )
        Picker.CLIPBOARD -> ChoiceDialog(
            "Clear copied OTP after",
            listOf(Choice(30, "30 seconds"), Choice(60, "1 minute"), Choice(120, "2 minutes"), Choice(0, "Never")),
            s.clipboardClearSec,
            onSelect = { v -> update { it.copy(clipboardClearSec = v) } },
            onDismiss = { picker = null },
        )
        Picker.OTP_DELETE -> ChoiceDialog(
            "Delete OTP messages older than",
            listOf(Choice(0, "Never"), Choice(1, "1 hour"), Choice(24, "24 hours"), Choice(24 * 7, "7 days")),
            s.otpAutoDeleteHours,
            onSelect = { v ->
                update { it.copy(otpAutoDeleteHours = v) }
                container.repo.cleanupOldOtps(force = true)
            },
            onDismiss = { picker = null },
            footer = "Only works when BetterSMS is your default SMS app.",
        )
        Picker.THEME -> ChoiceDialog(
            "Theme",
            listOf(Choice(0, "System default"), Choice(1, "Light glass"), Choice(2, "Dark glass")),
            s.themeMode,
            onSelect = { v -> update { it.copy(themeMode = v) } },
            onDismiss = { picker = null },
        )
        null -> Unit
    }

    if (confirmWipe) {
        ConfirmDialog(
            title = "Erase BetterSMS data?",
            message = "Settings, blocked senders and custom categories will be deleted and the encryption key destroyed. Your SMS messages are not affected.",
            confirmLabel = "Erase",
            destructive = true,
            onConfirm = {
                withAuth("Erase BetterSMS data") {
                    container.store.wipe()
                    container.lock.onLockDisabled()
                    Toast.makeText(ctx, "BetterSMS data erased", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { confirmWipe = false },
        )
    }
}

private fun lockTimeoutLabel(sec: Int) = when (sec) {
    0 -> "Immediately when you leave the app"
    else -> "After ${if (sec >= 60) "${sec / 60} min" else "$sec s"} in the background"
}

private fun clipboardLabel(sec: Int) = if (sec <= 0) "Never" else "After ${if (sec >= 60) "${sec / 60} min" else "$sec s"}"

private fun otpDeleteLabel(h: Int) = when (h) {
    0 -> "Off"
    1 -> "After 1 hour"
    24 -> "After 24 hours"
    else -> "After ${h / 24} days"
}

private fun themeLabel(m: Int) = when (m) {
    1 -> "Light glass"
    2 -> "Dark glass"
    else -> "System default"
}
