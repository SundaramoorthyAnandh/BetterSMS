package com.bettersms.app.ui.lock

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bettersms.app.container
import com.bettersms.app.security.BiometricAuth
import com.bettersms.app.security.BiometricStatus
import com.bettersms.app.ui.common.findActivity
import com.bettersms.app.ui.glass.GlassButton
import com.bettersms.app.ui.glass.GlassCard
import com.bettersms.app.ui.glass.glassSurface
import com.bettersms.app.ui.glass.rememberAnimationsEnabled
import com.bettersms.app.ui.theme.Glass

@Composable
fun LockScreen() {
    val ctx = LocalContext.current
    val c = Glass.colors
    val container = remember { ctx.container() }
    val lock = container.lock
    val generation by lock.generation.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var promptedFor by remember { mutableIntStateOf(-1) }
    var status by remember { mutableStateOf(BiometricAuth.status(ctx)) }

    val prompt: () -> Unit = prompt@{
        val activity = ctx.findActivity() as? FragmentActivity ?: return@prompt
        if (lock.authInProgress) return@prompt
        error = null
        BiometricAuth.authenticate(
            activity = activity,
            title = "Unlock BetterSMS",
            subtitle = "Touch the fingerprint sensor",
            lock = lock,
            onSuccess = { lock.unlock() },
            onError = { error = it },
            onEnrollmentChanged = {
                notice = "Fingerprints on this phone changed since your last unlock. If that wasn't you, check your phone's security settings."
            },
        )
    }

    LifecycleResumeEffect(generation) {
        status = BiometricAuth.status(ctx)
        if (status == BiometricStatus.READY && promptedFor != generation) {
            promptedFor = generation
            prompt()
        }
        onPauseOrDispose { }
    }

    val animate = rememberAnimationsEnabled()
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(1f, if (animate) 1.12f else 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")

    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .glassSurface(strong = true, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Lock, null, tint = c.accent, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("BetterSMS is locked", color = c.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Your messages and OTPs are protected", color = c.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(56.dp))

        if (status == BiometricStatus.READY) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(112.dp)
                        .scale(scale)
                        .border(2.dp, Brush.linearGradient(listOf(c.accent.copy(alpha = 0.6f), c.accentEnd.copy(alpha = 0.1f))), CircleShape),
                )
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(c.accent, c.accentEnd)))
                        .clickable(role = Role.Button, onClickLabel = "Unlock with fingerprint", onClick = prompt),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Fingerprint, "Unlock with fingerprint", tint = Color.White, modifier = Modifier.size(52.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Touch to unlock", color = c.textSecondary, fontSize = 14.sp)
        } else {
            GlassCard {
                Text(
                    "This phone has no screen lock or fingerprint set up, so BetterSMS can't verify it's you.",
                    color = c.textPrimary,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(14.dp))
                GlassButton("Open security settings", onClick = {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                })
                Spacer(Modifier.height(10.dp))
                GlassButton("Turn off app lock", contentColor = c.danger, onClick = {
                    container.store.updateSettings { it.copy(appLockEnabled = false) }
                    lock.onLockDisabled()
                })
            }
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = c.danger, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        notice?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = c.warning, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}
