package org.hnau.llmchat.app.hnauchat.messages

import org.hnau.llmchat.chat.api.ChatMessageId
import kotlin.time.Instant

data class MessageRecord(
    val role: MessageRole,
    val transportIds: List<ChatMessageId>,
    val text: String,
    val timestamp: Instant,
    val parentMessageId: StorageMessageId?,
    val summary: String?,
)