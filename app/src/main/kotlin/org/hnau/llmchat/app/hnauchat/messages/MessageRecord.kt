package org.hnau.llmchat.app.hnauchat.messages

import org.hnau.llmchat.chat.api.ChatId
import org.hnau.llmchat.chat.api.TransportMessageId
import kotlin.time.Instant

data class MessageRecord(
    val userId: ChatId,
    val role: MessageRole,
    val transportIds: List<TransportMessageId>,
    val text: String,
    val timestamp: Instant,
    val parentMessageId: MessageId?,
    val summary: String?,
)