package com.bettersms.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bettersms.app.classify.Category
import com.bettersms.app.classify.ThreatLevel
import com.bettersms.app.classify.TxnDirection
import com.bettersms.app.container
import com.bettersms.app.data.Conversation
import com.bettersms.app.data.SmsMessage
import com.bettersms.app.ui.common.Avatar
import com.bettersms.app.ui.common.EmptyState
import com.bettersms.app.ui.common.SMS_PERMISSIONS
import com.bettersms.app.ui.common.formatListTime
import com.bettersms.app.ui.common.rememberIsDefaultSmsApp
import com.bettersms.app.ui.common.rememberNow
import com.bettersms.app.ui.common.rememberRequestDefaultSms
import com.bettersms.app.ui.glass.GlassCard
import com.bettersms.app.ui.glass.GlassChip
import com.bettersms.app.ui.glass.GlassIconButton
import com.bettersms.app.ui.glass.GlassTextField
import com.bettersms.app.ui.glass.GradientButton
import com.bettersms.app.ui.glass.Pill
import com.bettersms.app.ui.theme.AllInboxIcon
import com.bettersms.app.ui.theme.Glass
import com.bettersms.app.ui.theme.color
import com.bettersms.app.ui.theme.icon

@Composable
fun HomeScreen(
    onOpenThread: (threadId: Long) -> Unit,
    onNewMessage: () -> Unit,
    onSettings: () -> Unit,
    onSecurity: () -> Unit,
) {
    val ctx = LocalContext.current
    val container = remember { ctx.container() }
    val state by container.repo.state.collectAsStateWithLifecycle()
    val c = Glass.colors
    var tabName by rememberSaveable { mutableStateOf<String?>(null) }
    val tab = tabName?.let { runCatching { Category.valueOf(it) }.getOrNull() }
    var query by rememberSaveable { mutableStateOf("") }
    val isDefault = rememberIsDefaultSmsApp()
    val now by rememberNow()
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        container.repo.start()
    }
    val requestDefault = rememberRequestDefaultSms { container.repo.start() }

    val q = query.trim()
    val rows: List<Pair<Conversation, SmsMessage>> = remember(state, tab, q) {
        state.conversations.mapNotNull { conv ->
            val base = if (tab == null) conv.latest else conv.latestByCategory[tab] ?: return@mapNotNull null
            if (q.isEmpty()) return@mapNotNull conv to base
            if (conv.displayName.contains(q, true) || conv.address.contains(q, true)) return@mapNotNull conv to base
            val hit = state.threads[conv.threadId]?.lastOrNull {
                it.body.contains(q, true) && (tab == null || it.category == tab)
            } ?: return@mapNotNull null
            conv to hit
        }.sortedByDescending { it.second.date }
    }
    val otps = remember(state, q) {
        state.otpMessages.filter { q.isEmpty() || it.body.contains(q, true) || it.address.contains(q, true) }
    }
    val activeOtps = otps.filter { it.date + it.otp!!.effectiveValidityMinutes * 60_000L > now && !it.threat.isThreat }.take(2)
    val nameFor: (SmsMessage) -> String = { m ->
        state.conversations.firstOrNull { it.threadId == m.threadId }?.displayName ?: m.address
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + 104.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Messages", style = androidx.compose.material3.MaterialTheme.typography.headlineLarge, color = c.textPrimary)
                        Text(
                            buildString {
                                append(if (state.totalUnread > 0) "${state.totalUnread} unread" else "All caught up")
                                if (activeOtps.isNotEmpty()) append(" · ${activeOtps.size} active OTP")
                                if (state.threatsDetected > 0) append(" · ${state.threatsDetected} threats caught")
                            },
                            color = c.textSecondary,
                            fontSize = 14.sp,
                        )
                    }
                    GlassIconButton(Icons.Rounded.Shield, "Security center", onSecurity, tint = c.accent)
                    Spacer(Modifier.width(10.dp))
                    GlassIconButton(Icons.Rounded.Settings, "Settings", onSettings)
                }
            }
            item(key = "search") {
                GlassTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search messages",
                    leadingIcon = Icons.Rounded.Search,
                    modifier = Modifier.fillMaxWidth(),
                    trailing = if (query.isNotEmpty()) {
                        {
                            Icon(
                                Icons.Rounded.Close, "Clear search", tint = c.textTertiary,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .clickable { query = "" },
                            )
                        }
                    } else null,
                )
            }
            item(key = "chips") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item {
                        GlassChip("All", tab == null, { tabName = null }, icon = AllInboxIcon, badge = state.totalUnread)
                    }
                    items(Category.entries.toList()) { cat ->
                        val badge = if (cat == Category.OTP) activeOtps.size else state.unreadByCategory[cat] ?: 0
                        GlassChip(cat.label, tab == cat, { tabName = cat.name }, icon = cat.icon, accent = cat.color, badge = badge)
                    }
                }
            }

            if (!state.loading && !state.hasPermission) {
                item(key = "perm") {
                    EmptyState(
                        Icons.Rounded.Sms,
                        "Allow SMS access",
                        "BetterSMS reads your messages on this phone only to sort them, find OTPs and catch scams. Nothing is uploaded — the app has no internet access.",
                    ) {
                        GradientButton("Allow access", onClick = { permLauncher.launch(SMS_PERMISSIONS) })
                    }
                }
                return@LazyColumn
            }

            if (!isDefault) {
                item(key = "default") {
                    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Shield, null, tint = c.accent, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Make BetterSMS your default SMS app to block senders, delete messages and auto-clean old OTPs.",
                                color = c.textSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(10.dp))
                            GradientButton("Set", onClick = requestDefault, height = 38.dp)
                        }
                    }
                }
            }

            if (tab == Category.OTP) {
                if (otps.isEmpty()) {
                    item(key = "otp-empty") {
                        EmptyState(Category.OTP.icon, "No OTPs yet", "One-time passwords will appear here with a live timer and one-tap copy.")
                    }
                }
                items(otps, key = { "otp-${it.id}" }) { m ->
                    OtpCard(m, nameFor(m), now, onOpen = { onOpenThread(m.threadId) })
                }
                return@LazyColumn
            }

            if (tab == null && q.isEmpty() && activeOtps.isNotEmpty()) {
                items(activeOtps, key = { "active-${it.id}" }) { m ->
                    OtpCard(m, nameFor(m), now, onOpen = { onOpenThread(m.threadId) })
                }
            }

            if (rows.isEmpty() && !state.loading) {
                item(key = "empty") {
                    when {
                        q.isNotEmpty() -> EmptyState(Icons.Rounded.SearchOff, "No matches", "Nothing matches \"$q\". Try a name, number or word from the message.")
                        tab == Category.SPAM -> EmptyState(Icons.Rounded.Shield, "No scams found", "Messages that look like phishing or fraud are moved here automatically.")
                        tab != null -> EmptyState(tab.icon, "Nothing in ${tab.label}", "Messages BetterSMS sorts into ${tab.label} will show up here.")
                        else -> EmptyState(Icons.Rounded.Inbox, "No messages", "Your conversations will appear here.")
                    }
                }
            }

            items(rows, key = { "c-${it.first.threadId}" }) { (conv, msg) ->
                ConversationRow(conv, msg, onClick = { onOpenThread(conv.threadId) })
            }
        }

        // Floating compose button
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp)
                .size(60.dp)
                .shadow(16.dp, CircleShape, ambientColor = c.accent, spotColor = c.accent)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(c.accent, c.accentEnd)))
                .clickable(role = Role.Button, onClick = onNewMessage)
                .semantics { contentDescription = "New message" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Edit, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun ConversationRow(conv: Conversation, msg: SmsMessage, onClick: () -> Unit) {
    val c = Glass.colors
    val unread = conv.unread > 0
    val category = if (msg.isIncoming) msg.category else conv.category
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(conv.displayName, conv.sender, if (conv.sender.isBusiness || category == Category.SPAM) category else null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        conv.displayName,
                        color = c.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatListTime(msg.date),
                        color = if (unread) c.accent else c.textTertiary,
                        fontSize = 12.sp,
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.Top) {
                    if (msg.threat.isThreat) {
                        Icon(
                            Icons.Rounded.Warning, "Suspicious",
                            tint = if (msg.threat.level == ThreatLevel.DANGEROUS) c.danger else c.warning,
                            modifier = Modifier.size(16.dp).padding(top = 1.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        (if (!msg.isIncoming) "You: " else "") + msg.body.replace('\n', ' '),
                        color = if (unread) c.textPrimary else c.textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(category.label, category.color, icon = category.icon)
                    msg.transaction?.let { t ->
                        Spacer(Modifier.width(6.dp))
                        val (label, col) = when (t.direction) {
                            TxnDirection.DEBIT -> "− ${t.amount}" to c.danger
                            TxnDirection.CREDIT -> "+ ${t.amount}" to c.success
                            TxnDirection.UNKNOWN -> t.amount to c.textSecondary
                        }
                        Pill(label, col)
                    }
                    msg.otp?.let {
                        Spacer(Modifier.width(6.dp))
                        Pill(it.code, Category.OTP.color)
                    }
                    if (conv.threatCount > 0 && !msg.threat.isThreat) {
                        Spacer(Modifier.width(6.dp))
                        Pill("${conv.threatCount} flagged", c.warning)
                    }
                    Spacer(Modifier.weight(1f))
                    if (unread) {
                        Box(
                            Modifier
                                .widthIn(min = 22.dp)
                                .height(22.dp)
                                .background(Brush.linearGradient(listOf(c.accent, c.accentEnd)), CircleShape)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (conv.unread > 99) "99+" else conv.unread.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
