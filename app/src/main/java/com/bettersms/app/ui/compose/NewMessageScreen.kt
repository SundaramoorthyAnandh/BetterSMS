package com.bettersms.app.ui.compose

import android.Manifest
import android.provider.ContactsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bettersms.app.container
import com.bettersms.app.ui.common.Avatar
import com.bettersms.app.ui.common.hasPermission
import com.bettersms.app.ui.glass.GlassCard
import com.bettersms.app.ui.glass.GlassIconButton
import com.bettersms.app.ui.glass.GlassTextField
import com.bettersms.app.ui.glass.SectionTitle
import com.bettersms.app.ui.theme.Glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Recipient(val name: String, val number: String)

/** Pick who to message; the chat screen does the actual sending. */
@Composable
fun NewMessageScreen(onBack: () -> Unit, onPick: (address: String) -> Unit) {
    val ctx = LocalContext.current
    val c = Glass.colors
    val repo = remember { ctx.container().repo }
    val state by repo.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<Recipient>>(emptyList()) }

    LaunchedEffect(query) {
        val q = query.trim()
        contacts = if (q.length < 2 || !ctx.hasPermission(Manifest.permission.READ_CONTACTS)) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI.buildUpon().appendPath(q).build()
                    ctx.contentResolver.query(
                        uri,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                        null, null, null,
                    )?.use { cur ->
                        buildList {
                            while (cur.moveToNext() && size < 20) {
                                val name = cur.getString(0) ?: continue
                                val num = cur.getString(1) ?: continue
                                add(Recipient(name, num))
                            }
                        }.distinctBy { it.number.filter(Char::isDigit).takeLast(10) }
                    }.orEmpty()
                }.getOrDefault(emptyList())
            }
        }
    }

    val q = query.trim()
    val recent = remember(state, q) {
        state.conversations
            .filter { it.sender.canReply }
            .filter { q.isEmpty() || it.displayName.contains(q, true) || it.address.contains(q, true) }
            .take(30)
    }
    val typedNumber = q.takeIf { it.count(Char::isDigit) >= 3 && it.all { ch -> ch.isDigit() || ch in "+ -()" } }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
            Text("New message", color = c.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        GlassTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Name or phone number",
            leadingIcon = Icons.Rounded.Person,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { typedNumber?.let(onPick) }),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (typedNumber != null) {
                item {
                    GlassCard(Modifier.fillMaxWidth(), strong = true, onClick = { onPick(typedNumber) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Rounded.Send, null, tint = c.accent)
                            Spacer(Modifier.width(12.dp))
                            Text("Send to $typedNumber", color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (contacts.isNotEmpty()) {
                item { SectionTitle("Contacts") }
                items(contacts, key = { "ct-" + it.number }) { r ->
                    RecipientRow(r.name, r.number) { onPick(r.number) }
                }
            }
            if (recent.isNotEmpty()) {
                item { SectionTitle("Recent") }
                items(recent, key = { "r-" + it.threadId }) { conv ->
                    RecipientRow(conv.displayName, conv.address) { onPick(conv.address) }
                }
            }
        }
    }
}

@Composable
private fun RecipientRow(name: String, number: String, onClick: () -> Unit) {
    val c = Glass.colors
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, null, null, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(name, color = c.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (name != number) Text(number, color = c.textSecondary, fontSize = 13.sp)
            }
        }
    }
}
