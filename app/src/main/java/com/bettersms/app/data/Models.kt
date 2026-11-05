package com.bettersms.app.data

import com.bettersms.app.classify.Category
import com.bettersms.app.classify.Classification
import com.bettersms.app.classify.SenderInfo

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
    val senderKey: String,
    val category: Category,
    val classification: Classification,
) {
    val isIncoming: Boolean get() = type == SmsType.INBOX
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
        if (threadId >= 0) all.firstOrNull { it.threadId == threadId }?.let { return it }
        if (key != null) all.firstOrNull { it.sender.key == key }?.let { return it }
        if (address != null) all.firstOrNull { it.address == address }?.let { return it }
        return null
    }
}
