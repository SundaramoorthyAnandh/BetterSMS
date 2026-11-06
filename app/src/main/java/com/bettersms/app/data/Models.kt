package com.bettersms.app.data

import com.bettersms.app.classify.Category
import com.bettersms.app.classify.Classification
import com.bettersms.app.classify.SenderInfo

/** Thread id meaning "not known yet"; threads that exist only locally use ids <= -10. */
const val NO_THREAD = -1L

/**
 * A message the user sent from BetterSMS. Kept (encrypted) until the system SMS store has a
 * matching row, so sent messages never disappear on phones that don't save them.
 */
data class OutgoingRecord(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val subId: Int,
    /** SmsType.OUTBOX while sending, then SENT or FAILED. */
    val status: Int,
)

object SmsType {
    const val INBOX = 1
    const val SENT = 2
    const val DRAFT = 3
    const val OUTBOX = 4
    const val FAILED = 5
    const val QUEUED = 6
}

data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val read: Boolean,
    /** SIM subscription the message used, or -1 if unknown. */
    val subId: Int,
    val senderKey: String,
    val category: Category,
    val classification: Classification,
) {
    val isIncoming: Boolean get() = type == SmsType.INBOX
    /** Only exists in BetterSMS's own sent log, not (yet) in the system SMS store. */
    val isLocal: Boolean get() = id < 0
    val otp get() = classification.otp
    val threat get() = classification.threat
    val transaction get() = classification.transaction
}

data class Conversation(
    val threadId: Long,
    val address: String,
    val sender: SenderInfo,
    val displayName: String,
    val isContact: Boolean,
    val latest: SmsMessage,
    val unread: Int,
    val messageCount: Int,
    /** User override, else category of the newest incoming message. */
    val category: Category,
    val categoryOverride: Category?,
    /** Newest message per category, so a bank can show up under both OTP and Transactions. */
    val latestByCategory: Map<Category, SmsMessage>,
    val unreadByCategory: Map<Category, Int>,
    val threatCount: Int,
    val blocked: Boolean,
    /** SIM the newest incoming message arrived on, or -1. */
    val lastIncomingSubId: Int = -1,
)

data class InboxState(
    val loading: Boolean = true,
    val hasPermission: Boolean = false,
    val conversations: List<Conversation> = emptyList(),
    val blockedConversations: List<Conversation> = emptyList(),
    val threads: Map<Long, List<SmsMessage>> = emptyMap(),
    /** Incoming OTP messages, newest first. */
    val otpMessages: List<SmsMessage> = emptyList(),
    val unreadByCategory: Map<Category, Int> = emptyMap(),
    val totalUnread: Int = 0,
    val threatsDetected: Int = 0,
) {
    fun findConversation(threadId: Long, address: String?, key: String?): Conversation? {
        val all = conversations + blockedConversations
        if (threadId != NO_THREAD) all.firstOrNull { it.threadId == threadId }?.let { return it }
        if (key != null) all.firstOrNull { it.sender.key == key }?.let { return it }
        if (address != null) all.firstOrNull { it.address == address }?.let { return it }
        return null
    }
}
