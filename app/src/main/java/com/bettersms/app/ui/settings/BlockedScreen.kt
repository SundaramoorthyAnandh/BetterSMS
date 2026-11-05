package com.bettersms.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bettersms.app.container
import com.bettersms.app.ui.common.Avatar
import com.bettersms.app.ui.common.EmptyState
import com.bettersms.app.ui.common.formatListTime
import com.bettersms.app.ui.glass.GlassButton
import com.bettersms.app.ui.glass.GlassCard
import com.bettersms.app.ui.glass.GlassIconButton
import com.bettersms.app.ui.theme.Glass

private data class BlockedEntry(val key: String, val name: String, val lastBody: String?, val lastDate: Long?, val count: Int, val threadId: Long?)

@Composable
fun BlockedScreen(onBack: () -> Unit, onOpenThread: (Long) -> Unit) {
    val ctx = LocalContext.current
    val c = Glass.colors
    val container = remember { ctx.container() }
    val data by container.store.data.collectAsStateWithLifecycle()
    val inbox by container.repo.state.collectAsStateWithLifecycle()

    val entries = remember(data.blocked, inbox) {
        data.blocked.map { key ->
            val conv = inbox.blockedConversations.firstOrNull { it.sender.key == key }
            BlockedEntry(
                key = key,
                name = conv?.displayName ?: key.substringAfter(':'),
                lastBody = conv?.latest?.body,
                lastDate = conv?.latest?.date,
                count = conv?.messageCount ?: 0,
                threadId = conv?.threadId,
            )
        }.sortedByDescending { it.lastDate ?: 0L }
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = bottom + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                Spacer(Modifier.width(12.dp))
                Text("Blocked senders", color = c.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (entries.isEmpty()) {
            item {
                EmptyState(Icons.Rounded.Block, "No one blocked", "Block a sender from the ⋮ menu inside a conversation. Their messages stay silent and out of your inbox.")
            }
        }
        items(entries, key = { it.key }) { e ->
            GlassCard(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                onClick = e.threadId?.let { id -> { onOpenThread(id) } },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(e.name, null, null, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, color = c.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                        Text(
                            e.lastBody?.let { "${formatListTime(e.lastDate ?: 0)} · $it" } ?: "No messages",
                            color = c.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassButton("Unblock", onClick = { container.repo.unblock(e.key) }, contentColor = c.accent, height = 38.dp)
                }
            }
        }
    }
}
