package com.bettersms.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.bettersms.app.classify.Category
import com.bettersms.app.classify.Classification
import com.bettersms.app.classify.MessageClassifier
import com.bettersms.app.classify.SenderAnalyzer
import com.bettersms.app.classify.SenderType
import com.bettersms.app.classify.ThreatLevel
import com.bettersms.app.sms.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads SMS from the system provider, classifies them on-device and exposes a ready-to-render
 * inbox. Message bodies never leave the process.
 */
@OptIn(FlowPreview::class)
class SmsRepository(
    private val context: Context,
    private val store: SecureStore,
    private val scope: CoroutineScope,
) {
    internal data class RawSms(
        val id: Long,
        val threadId: Long,
        val address: String,
        val body: String,
        val date: Long,
        val type: Int,
        val read: Boolean,
    )

    private val _state = MutableStateFlow(InboxState())
    val state: StateFlow<InboxState> = _state.asStateFlow()

    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val classificationCache = ConcurrentHashMap<String, Classification>()
    private val contactCache = ConcurrentHashMap<String, String>()
    @Volatile private var raw: List<RawSms> = emptyList()
    @Volatile private var observerRegistered = false
    @Volatile private var lastOtpCleanup = 0L

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = requestRefresh()
        override fun onChange(selfChange: Boolean, uri: Uri?) = requestRefresh()
    }

    @Volatile private var lastScamSetting = store.settings.scamProtection
    private val rebuildMutex = Mutex()

    init {
        scope.launch(Dispatchers.IO) {
            refreshRequests.debounce(250).collect { load() }
        }
        // Block list / overrides / settings changes only need a re-render, not a re-query.
        scope.launch(Dispatchers.IO) {
            store.data
                .map { Triple(it.blocked, it.overrides, it.readMarks) to it.settings.scamProtection }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (it.second != lastScamSetting) {
                        lastScamSetting = it.second
                        classificationCache.clear()
                    }
                    rebuild()
                }
        }
    }

    fun hasReadPermission(): Boolean = granted(Manifest.permission.READ_SMS)

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    /** Call after permissions or the default-app role change. */
    fun start() {
        if (hasReadPermission() && !observerRegistered) {
            runCatching {
                context.contentResolver.registerContentObserver(Uri.parse("content://sms"), true, observer)
                observerRegistered = true
            }
        }
        contactCache.clear()
        requestRefresh()
    }

    fun requestRefresh() {
        refreshRequests.tryEmit(Unit)
    }

    private suspend fun load() = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) {
            raw = emptyList()
            _state.value = InboxState(loading = false, hasPermission = false)
            return@withContext
        }
        val list = ArrayList<RawSms>(1024)
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                    Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ,
                ),
                null,
                null,
                "${Telephony.Sms.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext() && list.size < MAX_MESSAGES) {
                    val type = c.getInt(5)
                    if (type == SmsType.DRAFT) continue
                    val address = c.getString(2) ?: continue
                    list += RawSms(
                        id = c.getLong(0),
                        threadId = c.getLong(1),
                        address = address,
                        body = c.getString(3) ?: "",
                        date = c.getLong(4),
                        type = type,
                        read = c.getInt(6) != 0,
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query SMS", e)
        }
        raw = list
        rebuild()
        cleanupOldOtps()
    }

    private suspend fun rebuild() = rebuildMutex.withLock { rebuildLocked() }

    private fun rebuildLocked() {
        if (!hasReadPermission()) return
        val data = store.data.value
        val scam = data.settings.scamProtection
        val messages = raw.map { r ->
            val sender = SenderAnalyzer.analyze(r.address)
            val override = data.overrides[sender.key]
            val cls = if (r.type != SmsType.INBOX) {
                Classification.OUTGOING
            } else {
                classificationCache.getOrPut("${r.id}:${r.body.hashCode()}:${override != null}") {
                    val contact = contactName(r.address) != null
                    val c = MessageClassifier.classify(r.address, r.body, contact, scam)
                    // A user who moved a sender out of Spam trusts it.
                    if (override != null && override != Category.SPAM && c.category == Category.SPAM) {
                        c.copy(category = override)
                    } else {
                        c
                    }
                }
            }
            val category = when {
                r.type != SmsType.INBOX -> cls.category
                cls.otp != null -> Category.OTP
                override != null -> override
                else -> cls.category
            }
            SmsMessage(r.id, r.threadId, r.address, r.body, r.date, r.type, r.read, sender.key, category, cls)
        }

        val byThread = messages.groupBy { it.threadId }
        val conversations = ArrayList<Conversation>(byThread.size)
        val threads = HashMap<Long, List<SmsMessage>>(byThread.size)
        var threats = 0

        for ((threadId, msgs) in byThread) {
            // msgs are newest first (query order)
            val latest = msgs.first()
            val sender = SenderAnalyzer.analyze(latest.address)
            val name = contactName(latest.address)
            val readMark = maxOf(data.readMarks["t:$threadId"] ?: 0L, data.readMarks[sender.key] ?: 0L)
            val latestByCategory = HashMap<Category, SmsMessage>()
            val unreadByCategory = HashMap<Category, Int>()
            var unread = 0
            var threatCount = 0
            for (m in msgs) {
                if (!m.isIncoming) continue
                latestByCategory.putIfAbsent(m.category, m)
                if (m.threat.isThreat) threatCount++
                if (!m.read && m.date > readMark) {
                    unread++
                    unreadByCategory.merge(m.category, 1, Int::plus)
                }
            }
            if (sender.type == SenderType.PHONE && latestByCategory.isEmpty()) {
                // A thread with only outgoing messages is still a personal conversation.
                latestByCategory[Category.PERSONAL] = latest
            }
            threats += threatCount
            val override = data.overrides[sender.key]
            val newestIncoming = msgs.firstOrNull { it.isIncoming }
            conversations += Conversation(
                threadId = threadId,
                address = latest.address,
                sender = sender,
                displayName = name ?: sender.displayName,
                isContact = name != null,
                latest = latest,
                unread = unread,
                messageCount = msgs.size,
                category = override ?: newestIncoming?.category ?: Category.PERSONAL,
                categoryOverride = override,
                latestByCategory = latestByCategory,
                unreadByCategory = unreadByCategory,
                threatCount = threatCount,
                blocked = sender.key in data.blocked,
            )
            threads[threadId] = msgs.asReversed()
        }

        conversations.sortByDescending { it.latest.date }
        val (blocked, visible) = conversations.partition { it.blocked }
        val unreadByCategory = HashMap<Category, Int>()
        visible.forEach { c -> c.unreadByCategory.forEach { (k, v) -> unreadByCategory.merge(k, v, Int::plus) } }
        val otps = messages.filter { it.isIncoming && it.otp != null && it.senderKey !in data.blocked }

        _state.value = InboxState(
            loading = false,
            hasPermission = true,
            conversations = visible,
            blockedConversations = blocked,
            threads = threads,
            otpMessages = otps,
            unreadByCategory = unreadByCategory,
            totalUnread = visible.sumOf { it.unread },
            threatsDetected = threats,
        )
    }

    /** Test hook: render the inbox from in-memory messages instead of the system provider. */
    @VisibleForTesting
    internal suspend fun injectForTest(messages: List<RawSms>) {
        raw = messages
        rebuild()
    }

    /** Contact display name for a phone number, or null. Cached; safe to call off the main thread. */
    fun contactName(address: String): String? {
        val sender = SenderAnalyzer.analyze(address)
        if (sender.isBusiness || !granted(Manifest.permission.READ_CONTACTS)) return null
        contactCache[address]?.let { return it.ifEmpty { null } }
        val name = try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            null
        }
        contactCache[address] = name ?: ""
        return name
    }

    fun classifyIncoming(address: String, body: String): Classification {
        val sender = SenderAnalyzer.analyze(address)
        val data = store.data.value
        val override = data.overrides[sender.key]
        val c = MessageClassifier.classify(address, body, contactName(address) != null, data.settings.scamProtection)
        return if (override != null && override != Category.SPAM && c.category == Category.SPAM) c.copy(category = override) else c
    }

    fun markRead(threadId: Long, senderKey: String?) {
        val now = System.currentTimeMillis()
        store.update { d ->
            val marks = d.readMarks.toMutableMap()
            if (threadId >= 0) marks["t:$threadId"] = now
            if (senderKey != null) marks[senderKey] = now
            // Keep the map bounded.
            val trimmed = if (marks.size > 600) marks.entries.sortedByDescending { it.value }.take(500).associate { it.toPair() } else marks
            d.copy(readMarks = trimmed)
        }
        if (threadId >= 0) scope.launch(Dispatchers.IO) { SmsProvider.markThreadRead(context, threadId) }
    }

    fun markReadByAddress(address: String) {
        val key = SenderAnalyzer.keyOf(address)
        val threadId = _state.value.findConversation(-1, address, key)?.threadId ?: -1
        markRead(threadId, key)
    }

    fun block(senderKey: String) = store.update { it.copy(blocked = it.blocked + senderKey) }

    fun unblock(senderKey: String) = store.update { it.copy(blocked = it.blocked - senderKey) }

    fun isBlocked(address: String): Boolean = SenderAnalyzer.keyOf(address) in store.data.value.blocked

    fun setCategoryOverride(senderKey: String, category: Category?) {
        classificationCache.clear()
        store.update {
            it.copy(overrides = if (category == null) it.overrides - senderKey else it.overrides + (senderKey to category))
        }
    }

    suspend fun deleteThread(threadId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!SmsRole.isDefault(context)) return@withContext false
        SmsProvider.deleteThread(context, threadId).also { requestRefresh() }
    }

    suspend fun deleteMessage(id: Long): Boolean = withContext(Dispatchers.IO) {
        if (!SmsRole.isDefault(context)) return@withContext false
        (SmsProvider.deleteMessages(context, listOf(id)) > 0).also { requestRefresh() }
    }

    suspend fun send(address: String, body: String): Boolean = withContext(Dispatchers.IO) {
        SmsSender.send(context, address, body).also { requestRefresh() }
    }

    /** Deletes OTP messages older than the configured age. Only possible as the default SMS app. */
    fun cleanupOldOtps(force: Boolean = false) {
        val hours = store.settings.otpAutoDeleteHours
        if (hours <= 0 || !SmsRole.isDefault(context)) return
        val now = System.currentTimeMillis()
        if (!force && now - lastOtpCleanup < 10 * 60_000L) return
        lastOtpCleanup = now
        val cutoff = now - hours * 3_600_000L
        val ids = _state.value.otpMessages.filter { it.date < cutoff }.map { it.id }
        if (ids.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val n = SmsProvider.deleteMessages(context, ids)
            Log.i(TAG, "Auto-deleted $n expired OTP messages")
        }
    }

    fun countThreats(): Int = _state.value.threatsDetected

    fun dangerousCount(): Int = _state.value.conversations.sumOf { c ->
        _state.value.threads[c.threadId]?.count { it.threat.level == ThreatLevel.DANGEROUS } ?: 0
    }

    private companion object {
        const val TAG = "SmsRepository"
        const val MAX_MESSAGES = 6000
    }
}
