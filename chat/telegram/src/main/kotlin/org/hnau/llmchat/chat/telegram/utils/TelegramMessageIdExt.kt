package org.hnau.llmchat.chat.telegram.utils

import org.hnau.llmchat.chat.api.MessageId
import dev.inmo.tgbotapi.types.MessageId as TelegramMessageId

internal fun TelegramMessageId.toMessageId(): MessageId = MessageId(
    id = long.toString(),
)