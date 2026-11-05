package com.bettersms.app.ui.conversation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.telephony.SmsMessage as TelSmsMessage
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bettersms.app.classify.Category
import com.bettersms.app.classify.LinkInfo
import com.bettersms.app.classify.SenderAnalyzer
import com.bettersms.app.classify.SenderType
import com.bettersms.app.classify.ThreatLevel
import com.bettersms.app.classify.TxnDirection
import com.bettersms.app.container
import com.bettersms.app.data.SmsMessage
import com.bettersms.app.data.SmsType
import com.bettersms.app.security.ClipboardGuard
import com.bettersms.app.ui.common.Avatar
import com.bettersms.app.ui.common.Choice
import com.bettersms.app.ui.common.ChoiceDialog
import com.bettersms.app.ui.common.ConfirmDialog
import com.bettersms.app.ui.common.LinkSafetyDialog
import com.bettersms.app.ui.common.dayOf
import com.bettersms.app.ui.common.formatDayHeader
import com.bettersms.app.ui.common.formatTime
import com.bettersms.app.ui.common.hasPermission
import com.bettersms.app.ui.common.openUrl
import com.bettersms.app.ui.common.rememberIsDefaultSmsApp
import com.bettersms.app.ui.common.rememberRequestDefaultSms
import com.bettersms.app.ui.glass.GlassCard
import com.bettersms.app.ui.glass.GlassIconButton
import com.bettersms.app.ui.glass.GlassTextField
import com.bettersms.app.ui.glass.Pill
import com.bettersms.app.ui.glass.glassSurface
import com.bettersms.app.ui.home.copyOtp
import com.bettersms.app.ui.theme.Glass
import com.bettersms.app.ui.theme.color
import com.bettersms.app.ui.theme.icon
import kotlinx.coroutines.launch
import java.time.LocalDate

private sealed interface ChatItem {
    val key: String

    data class Day(val day: LocalDate) : ChatItem {
        override val key get() = "d-$day"
    }

    data class Msg(val m: SmsMessage) : ChatItem {
        override val key get() = "m-${m.id}"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationScreen(
    threadId: Long,
    address: String?,
    initialBody: String?,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val container = remember { ctx.container() }
    val repo = container.repo
    val state by repo.state.collectAsStateWithLifecycle()
    val settings by container.store.data.collectAsStateWithLifecycle()
    val c = Glass.colors
    val scope = rememberCoroutineScope()
    val isDefault = rememberIsDefaultSmsApp()
    val requestDefault = rememberRequestDefaultSms { repo.start() }

    val addressKey = remember(address) { address?.let { SenderAnalyzer.keyOf(it) } }
    val conv = remember(state, threadId, address) { state.findConversation(threadId, address, addressKey) }
    val messages = remember(state, conv) { conv?.let { state.threads[it.threadId] }.orEmpty() }
    val targetAddress = conv?.address ?: address.orEmpty()
    val sender = remember(targetAddress) { SenderAnalyzer.analyze(targetAddress) }
    val title = conv?.displayName ?: remember(targetAddress) { repo.contactName(targetAddress) } ?: sender.displayName
    val blocked = sender.key in settings.blocked
    val threatCount = messages.count { it.threat.isThreat }

    var text by rememberSaveable { mutableStateOf(initialBody.orEmpty()) }
    var menuOpen by remember { mutableStateOf(false) }
    var linkToConfirm by remember { mutableStateOf<LinkInfo?>(null) }
    var showCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var messageMenu by remember { mutableStateOf<SmsMessage?>(null) }

    val sendPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(ctx, "SMS permission is needed to send", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(conv?.threadId, messages.size) {
        conv?.let { repo.markRead(it.threadId, it.sender.key) }
    }

    val items = remember(messages) {
        buildList {
            var lastDay: LocalDate? = null
            for (m in messages) {
                val d = dayOf(m.date)
                if (d != lastDay) {
                    add(ChatItem.Day(d))
                    lastDay = d
                }
                add(ChatItem.Msg(m))
            }
        }.asReversed()
    }
    val listState = rememberLazyListState()
    LaunchedEffect(items.size) { if (items.isNotEmpty()) listState.animateScrollToItem(0) }

    val onLink: (LinkInfo) -> Unit = { link ->
        if (!link.suspicious && !settings.settings.linkWarnings) openUrl(ctx, link.url) else linkToConfirm = link
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // Top bar
        Row(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .glassSurface(strong = true, shape = RoundedCornerShape(28.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, size = 40.dp)
            Spacer(Modifier.width(8.dp))
            Avatar(title, sender, if (sender.isBusiness || conv?.category == Category.SPAM) conv?.category else null, size = 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = c.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        if (title != targetAddress) append(targetAddress)
                        conv?.let {
                            if (isNotEmpty()) append(" · ")
                            append(it.category.label)
                        }
                    }.ifEmpty { "New conversation" },
                    color = c.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            if (sender.type == SenderType.PHONE) {
                GlassIconButton(Icons.Rounded.Call, "Call", {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$targetAddress"))) }
                }, size = 40.dp)
                Spacer(Modifier.width(6.dp))
            }
            Box {
                GlassIconButton(Icons.Rounded.MoreVert, "More options", { menuOpen = true }, size = 40.dp)
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = c.dialog,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text(if (blocked) "Unblock sender" else "Block sender", color = c.textPrimary) },
                        leadingIcon = { Icon(Icons.Rounded.Block, null, tint = c.danger) },
                        onClick = {
                            menuOpen = false
                            if (blocked) repo.unblock(sender.key) else repo.block(sender.key)
                            Toast.makeText(ctx, if (blocked) "Unblocked" else "Blocked · no more alerts from $title", Toast.LENGTH_SHORT).show()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Move to category…", color = c.textPrimary) },
                        leadingIcon = { Icon(Icons.Rounded.Category, null, tint = c.accent) },
                        onClick = {
                            menuOpen = false
                            showCategory = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy number", color = c.textPrimary) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, tint = c.textSecondary) },
                        onClick = {
                            menuOpen = false
                            ClipboardGuard.copyPlain(ctx, targetAddress, "Number")
                        },
                    )
                    if (conv != null) {
                        DropdownMenuItem(
                            text = { Text("Delete conversation", color = c.danger) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = c.danger) },
                            onClick = {
                                menuOpen = false
                                if (isDefault) confirmDelete = true else requestDefault()
                            },
                        )
                    }
                }
            }
        }

        if (blocked) {
            Banner(
                icon = Icons.Rounded.Block,
                color = c.danger,
                text = "You blocked this sender. New messages are hidden and silent.",
                action = "Unblock",
                onAction = { repo.unblock(sender.key) },
            )
        } else if (threatCount > 0) {
            Banner(
                icon = Icons.Rounded.GppBad,
                color = if (messages.any { it.threat.level == ThreatLevel.DANGEROUS }) c.danger else c.warning,
                text = "BetterSMS flagged $threatCount message${if (threatCount > 1) "s" else ""} here as a possible scam. Don't open links, call back or share any OTP.",
                action = if (conv?.categoryOverride == null) "Block" else null,
                onAction = { repo.block(sender.key) },
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items, key = { it.key }) { item ->
                when (item) {
                    is ChatItem.Day -> DayHeader(item.day)
                    is ChatItem.Msg -> MessageBubble(
                        m = item.m,
                        onLink = onLink,
                        onLongPress = { messageMenu = item.m },
                    )
                }
            }
        }

        // Composer
        if (sender.canReply && targetAddress.isNotBlank()) {
            Row(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    GlassTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = "Message",
                        singleLine = false,
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (text.length > 60) {
                        val info = remember(text) { runCatching { TelSmsMessage.calculateLength(text, false) }.getOrNull() }
                        if (info != null) {
                            Text(
                                "${info[2]} left · ${info[0]} SMS",
                                color = c.textTertiary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                val canSend = text.isNotBlank()
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) Brush.linearGradient(listOf(c.accent, c.accentEnd))
                            else Brush.linearGradient(listOf(c.glassFillStrong, c.glassFillStrong)),
                        )
                        .clickable(enabled = canSend, role = Role.Button) {
                            if (!ctx.hasPermission(Manifest.permission.SEND_SMS)) {
                                sendPermission.launch(Manifest.permission.SEND_SMS)
                                return@clickable
                            }
                            val body = text.trim()
                            text = ""
                            scope.launch {
                                val ok = repo.send(targetAddress, body)
                                if (!ok) {
                                    text = body
                                    Toast.makeText(ctx, "Couldn't send. Check SIM and signal.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = if (canSend) Color.White else c.textTertiary, modifier = Modifier.size(22.dp))
                }
            }
        } else if (targetAddress.isNotBlank()) {
            Row(
                Modifier
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Info, null, tint = c.textTertiary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("This sender can't receive replies", color = c.textTertiary, fontSize = 13.sp)
            }
        }
    }

    linkToConfirm?.let { LinkSafetyDialog(it) { linkToConfirm = null } }

    if (showCategory) {
        val choices = listOf(Choice<Category?>(null, "Automatic (smart)")) +
            Category.entries.filter { it != Category.OTP }.map { Choice<Category?>(it, it.label) }
        ChoiceDialog(
            title = "Always show $title in",
            choices = choices,
            selected = conv?.categoryOverride,
            onSelect = { repo.setCategoryOverride(sender.key, it) },
            onDismiss = { showCategory = false },
            footer = "OTP codes are always detected automatically.",
        )
    }

    if (confirmDelete && conv != null) {
        ConfirmDialog(
            title = "Delete conversation with $title?",
            message = "All ${conv.messageCount} messages will be permanently deleted from this phone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                scope.launch {
                    if (repo.deleteThread(conv.threadId)) onBack()
                    else Toast.makeText(ctx, "Couldn't delete. Make BetterSMS the default SMS app first.", Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }

    messageMenu?.let { m ->
        MessageActionsDialog(
            m = m,
            canDelete = isDefault,
            onDismiss = { messageMenu = null },
            onCopy = { ClipboardGuard.copyPlain(ctx, m.body, "Message") },
            onDelete = {
                scope.launch {
                    if (!repo.deleteMessage(m.id)) Toast.makeText(ctx, "Couldn't delete this message", Toast.LENGTH_SHORT).show()
                }
            },
            onRequestDefault = requestDefault,
        )
    }
}

@Composable
private fun Banner(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, text: String, action: String?, onAction: () -> Unit) {
    val c = Glass.colors
    GlassCard(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth(),
        tint = color,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, color = c.textPrimary, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            if (action != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    action,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onAction)
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            formatDayHeader(day),
            color = Glass.colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(CircleShape)
                .glassSurface(shape = CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(m: SmsMessage, onLink: (LinkInfo) -> Unit, onLongPress: () -> Unit) {
    val c = Glass.colors
    val ctx = LocalContext.current
    val incoming = m.isIncoming
    val shape = if (incoming) RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp) else RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
    val threat = m.threat
    val danger = threat.level == ThreatLevel.DANGEROUS
    val textColor = if (incoming) c.textPrimary else Color.White
    val linkColor = when {
        !incoming -> Color.White
        danger -> c.danger
        else -> c.accent
    }

    val annotated = remember(m.body, threat.links, linkColor) {
        buildAnnotatedString {
            append(m.body)
            threat.links.forEach { link ->
                if (link.start in 0..m.body.length && link.end in link.start..m.body.length) {
                    addLink(
                        LinkAnnotation.Clickable(
                            tag = link.url,
                            styles = TextLinkStyles(
                                SpanStyle(
                                    color = if (link.suspicious) c.danger else linkColor,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = if (link.suspicious) FontWeight.SemiBold else null,
                                ),
                            ),
                            linkInteractionListener = { onLink(link) },
                        ),
                        link.start,
                        link.end,
                    )
                }
            }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (incoming) Arrangement.Start else Arrangement.End) {
        Column(horizontalAlignment = if (incoming) Alignment.Start else Alignment.End, modifier = Modifier.widthIn(max = 310.dp)) {
            Column(
                Modifier
                    .clip(shape)
                    .then(
                        if (incoming) Modifier.glassSurface(strong = true, tint = if (threat.isThreat) (if (danger) c.danger else c.warning) else null, shape = shape)
                        else Modifier.background(Brush.linearGradient(listOf(c.accent, c.accentEnd)), shape),
                    )
                    .then(if (danger) Modifier.border(1.5.dp, c.danger.copy(alpha = 0.7f), shape) else Modifier)
                    .combinedClickable(onClick = {}, onLongClick = onLongPress)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(annotated, color = textColor, fontSize = 15.sp, lineHeight = 21.sp)

                m.otp?.let { otp ->
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Category.OTP.color.copy(alpha = if (c.isDark) 0.25f else 0.12f))
                            .clickable { copyOtp(ctx, otp.code) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Key, null, tint = Category.OTP.color, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(otp.code, color = c.textPrimary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Rounded.ContentCopy, "Copy OTP", tint = Category.OTP.color, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy", color = Category.OTP.color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                m.transaction?.let { t ->
                    Spacer(Modifier.height(8.dp))
                    val (label, col) = when (t.direction) {
                        TxnDirection.DEBIT -> "Debited ${t.amount}" to c.danger
                        TxnDirection.CREDIT -> "Credited ${t.amount}" to c.success
                        TxnDirection.UNKNOWN -> t.amount to c.textSecondary
                    }
                    Pill(label, col, icon = m.category.icon)
                }
            }
            if (threat.isThreat) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Warning, null, tint = if (danger) c.danger else c.warning, modifier = Modifier.size(14.dp).padding(top = 1.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        (if (danger) "Likely scam: " else "Be careful: ") + threat.reasons.take(3).joinToString(" · "),
                        color = if (danger) c.danger else c.warning,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                formatTime(m.date) + when (m.type) {
                    SmsType.OUTBOX, SmsType.QUEUED -> " · Sending…"
                    SmsType.FAILED -> " · Failed"
                    else -> ""
                },
                color = if (m.type == SmsType.FAILED) c.danger else c.textTertiary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun MessageActionsDialog(
    m: SmsMessage,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRequestDefault: () -> Unit,
) {
    val c = Glass.colors
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    if (confirm) {
        ConfirmDialog(
            title = "Delete this message?",
            message = "It will be permanently removed from this phone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = onDelete,
            onDismiss = {
                confirm = false
                onDismiss()
            },
        )
        return
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.dialog,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Message", color = c.textPrimary) },
        text = {
            Column {
                ActionRow(Icons.Rounded.ContentCopy, "Copy text") {
                    onCopy()
                    onDismiss()
                }
                m.otp?.let { otp ->
                    ActionRow(Icons.Rounded.Key, "Copy OTP ${otp.code}") {
                        copyOtp(ctx, otp.code)
                        onDismiss()
                    }
                }
                ActionRow(Icons.Rounded.Delete, if (canDelete) "Delete" else "Delete (set as default app)", tint = c.danger) {
                    if (canDelete) confirm = true else {
                        onDismiss()
                        onRequestDefault()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close", color = c.accent) } },
    )
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Glass.colors.textPrimary, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(14.dp))
        Text(label, color = tint, fontSize = 16.sp)
    }
}
