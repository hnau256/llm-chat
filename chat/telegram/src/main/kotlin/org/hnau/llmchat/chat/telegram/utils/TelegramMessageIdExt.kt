package org.hnau.llmchat.chat.telegram.utils

import org.hnau.llmchat.chat.api.ChatMessageId
import dev.inmo.tgbotapi.types.MessageId as TelegramMessageId

internal fun TelegramMessageId.toMessageId(): ChatMessageId = ChatMessageId(
    id = long.toString(),
)