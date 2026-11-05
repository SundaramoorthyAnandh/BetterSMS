package com.bettersms.app.ui.glass

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bettersms.app.ui.theme.Glass
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@Composable
fun rememberAnimationsEnabled(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/** Soft, slowly drifting colour blobs behind everything — the "glass" needs something to frost. */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = Glass.colors
    val animate = rememberAnimationsEnabled()
    val transition = rememberInfiniteTransition(label = "blobs")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "phase",
    )
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.bgTop, c.bgBottom)))
            .drawBehind {
                val p = if (animate) phase.value else 0.3f
                val t = p * 2f * PI.toFloat()
                val w = size.width
                val h = size.height
                val r = max(w, h)
                fun blob(color: Color, cx: Float, cy: Float, radius: Float) {
                    val center = Offset(cx, cy)
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to color,
                            0.55f to color.copy(alpha = color.alpha * 0.45f),
                            1f to color.copy(alpha = 0f),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
                blob(c.blobs[0], w * (0.10f + 0.12f * sin(t)), h * (0.10f + 0.05f * cos(t)), r * 0.62f)
                blob(c.blobs[1], w * (0.98f - 0.12f * cos(t)), h * (0.32f + 0.07f * sin(t)), r * 0.55f)
                blob(c.blobs[2], w * (0.15f + 0.14f * cos(t)), h * (0.78f - 0.06f * sin(t)), r * 0.55f)
                blob(c.blobs[3], w * (0.90f + 0.06f * sin(t)), h * (1.00f - 0.06f * cos(t)), r * 0.48f)
            },
        content = content,
    )
}

/** Frosted, translucent card with a light-catching gradient rim. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    strong: Boolean = false,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Glass.colors
    Column(
        modifier
            .clip(shape)
            .glassSurface(strong = strong, tint = tint, shape = shape)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick, role = Role.Button)
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun Modifier.glassSurface(strong: Boolean = false, tint: Color? = null, shape: Shape = RoundedCornerShape(24.dp)): Modifier {
    val c = Glass.colors
    val fill = if (strong) c.glassFillStrong else c.glassFill
    return this
        .background(fill, shape)
        .then(if (tint != null) Modifier.background(tint.copy(alpha = if (c.isDark) 0.16f else 0.10f), shape) else Modifier)
        .background(Brush.verticalGradient(0f to c.glassHighlight, 0.55f to Color.Transparent), shape)
        .border(1.dp, Brush.verticalGradient(listOf(c.glassBorderTop, c.glassBorderBottom)), shape)
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = Glass.colors.textPrimary,
    badge: Boolean = false,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .glassSurface(shape = CircleShape)
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(9.dp)
                    .size(8.dp)
                    .background(Glass.colors.danger, CircleShape),
            )
        }
    }
}

@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = Glass.colors.accent,
    badge: Int = 0,
) {
    val c = Glass.colors
    val shape = CircleShape
    Row(
        modifier
            .height(40.dp)
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.78f))), shape)
                } else {
                    Modifier.glassSurface(shape = shape)
                },
            )
            .clickable(onClick = onClick, role = Role.Tab)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (selected) Color.White else c.textPrimary
        if (icon != null) {
            Icon(icon, null, tint = if (selected) Color.White else accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        if (badge > 0) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .heightIn(min = 18.dp)
                    .background(if (selected) Color.White.copy(alpha = 0.3f) else accent, CircleShape)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (badge > 99) "99+" else badge.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    colors: List<Color> = listOf(Glass.colors.accent, Glass.colors.accentEnd),
    height: Dp = 52.dp,
) {
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(Brush.horizontalGradient(colors), CircleShape)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    contentColor: Color = Glass.colors.textPrimary,
    height: Dp = 48.dp,
) {
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .glassSurface(strong = true, shape = CircleShape)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = contentColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(26.dp),
) {
    val c = Glass.colors
    Row(
        modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .glassSurface(strong = true, shape = shape)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, null, tint = c.textTertiary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
            if (value.isEmpty()) {
                Text(placeholder, color = c.textTertiary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else maxLines,
                textStyle = TextStyle(color = c.textPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            trailing()
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier
            .background(color.copy(alpha = if (Glass.colors.isDark) 0.22f else 0.14f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 6.dp, bottom = 8.dp, top = 4.dp),
        color = Glass.colors.textTertiary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
    )
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = Glass.colors.accent,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Glass.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(iconTint.copy(alpha = if (c.isDark) 0.22f else 0.13f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = c.textSecondary, fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = Glass.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = c.accent,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = c.textTertiary.copy(alpha = 0.35f),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
fun GlassDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 66.dp)
            .height(1.dp)
            .background(Glass.colors.divider),
    )
}
