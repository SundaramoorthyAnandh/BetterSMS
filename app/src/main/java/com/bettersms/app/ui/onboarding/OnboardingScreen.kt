package com.bettersms.app.ui.onboarding

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.bettersms.app.container
import com.bettersms.app.security.BiometricAuth
import com.bettersms.app.security.BiometricStatus
import com.bettersms.app.ui.common.SMS_PERMISSIONS
import com.bettersms.app.ui.common.findActivity
import com.bettersms.app.ui.common.rememberIsDefaultSmsApp
import com.bettersms.app.ui.common.rememberPermissionState
import com.bettersms.app.ui.common.rememberRequestDefaultSms
import com.bettersms.app.ui.glass.GlassButton
import com.bettersms.app.ui.glass.GlassCard
import com.bettersms.app.ui.glass.GradientButton
import com.bettersms.app.ui.glass.glassSurface
import com.bettersms.app.ui.theme.Glass

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val c = Glass.colors
    val container = remember { ctx.container() }
    val smsGranted = rememberPermissionState(Manifest.permission.READ_SMS)
    val isDefault = rememberIsDefaultSmsApp()
    var lockOn by remember { mutableStateOf(container.store.settings.appLockEnabled) }
    var bioStatus by remember { mutableStateOf(BiometricAuth.status(ctx)) }
    LifecycleResumeEffect(Unit) {
        bioStatus = BiometricAuth.status(ctx)
        onPauseOrDispose { }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        container.repo.start()
    }
    val requestDefault = rememberRequestDefaultSms { container.repo.start() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .size(88.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF3FA9FF), c.accent, Color(0xFF8B5CF6))), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Sms, null, tint = Color.White, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("BetterSMS", color = c.textPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Smart. Private. Locked down.", color = c.textSecondary, fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Feature(Icons.Rounded.Category, "Sorted for you", "Personal, OTP, transactions, bills, offers and updates — automatically.")
            Feature(Icons.Rounded.Key, "OTP assistant", "Big code, live expiry timer, one-tap copy and auto-clearing clipboard.")
            Feature(Icons.Rounded.GppGood, "Scam shield", "Flags phishing links, fake KYC, lottery and job scams before you tap.")
            Feature(Icons.Rounded.Fingerprint, "Fingerprint lock", "Only you can open your messages.")
            Feature(Icons.Rounded.CloudOff, "100% on-device", "No internet permission. Nothing ever leaves your phone.")
        }

        Spacer(Modifier.height(20.dp))
        Text("Set up in 3 steps", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(start = 4.dp))
        Spacer(Modifier.height(10.dp))

        Step(
            number = 1,
            title = "Allow SMS access",
            body = "Needed to read, sort and protect your messages.",
            done = smsGranted,
            action = "Allow",
            onAction = { permLauncher.launch(SMS_PERMISSIONS) },
        )
        Spacer(Modifier.height(10.dp))
        Step(
            number = 2,
            title = "Make it your SMS app",
            body = "Recommended. Lets BetterSMS block senders, delete messages and auto-clean old OTPs. Picture messages (MMS) aren't supported yet.",
            done = isDefault,
            action = "Set default",
            onAction = requestDefault,
        )
        Spacer(Modifier.height(10.dp))
        Step(
            number = 3,
            title = "Turn on fingerprint lock",
            body = when (bioStatus) {
                BiometricStatus.READY -> "Unlock with your fingerprint (or phone PIN as a backup)."
                BiometricStatus.NOT_ENROLLED -> "Add a fingerprint or screen lock in phone settings first."
                else -> "Fingerprint isn't available on this phone; your screen lock will be used."
            },
            done = lockOn,
            action = if (bioStatus == BiometricStatus.NOT_ENROLLED) "Set up" else "Enable",
            onAction = {
                if (bioStatus == BiometricStatus.READY) {
                    val activity = ctx.findActivity() as? FragmentActivity ?: return@Step
                    BiometricAuth.authenticate(
                        activity, "Enable fingerprint lock", "Confirm it's you", container.lock,
                        onSuccess = {
                            container.store.updateSettings { it.copy(appLockEnabled = true) }
                            container.lock.unlock()
                            lockOn = true
                        },
                        onError = { msg -> if (msg != null) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() },
                    )
                } else {
                    runCatching { ctx.startActivity(BiometricAuth.enrollIntent()) }
                }
            },
        )

        Spacer(Modifier.height(28.dp))
        GradientButton(
            if (smsGranted) "Get started" else "Continue without access",
            onClick = {
                container.store.updateSettings { it.copy(onboardingDone = true) }
                container.repo.start()
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "You can change all of this later in Settings.",
            color = c.textTertiary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    val c = Glass.colors
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(36.dp)
                .background(c.accent.copy(alpha = if (c.isDark) 0.22f else 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = c.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(body, color = c.textSecondary, fontSize = 13.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun Step(number: Int, title: String, body: String, done: Boolean, action: String, onAction: () -> Unit) {
    val c = Glass.colors
    GlassCard(Modifier.fillMaxWidth(), strong = !done) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(32.dp)
                    .then(
                        if (done) Modifier.background(c.success, CircleShape)
                        else Modifier.glassSurface(strong = true, shape = CircleShape),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                else Text("$number", color = c.accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = c.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if (done) Text("Done", color = c.success, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Spacer(Modifier.height(2.dp))
                Text(body, color = c.textSecondary, fontSize = 13.sp, lineHeight = 17.sp)
                if (!done) {
                    Spacer(Modifier.height(10.dp))
                    GlassButton(action, onClick = onAction, contentColor = c.accent, height = 40.dp)
                }
            }
        }
    }
}
