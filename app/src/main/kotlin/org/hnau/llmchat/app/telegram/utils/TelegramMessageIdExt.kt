package org.hnau.llmchat.app.telegram.utils

import org.hnau.llmchat.app.chat.MessageId
import dev.inmo.tgbotapi.types.MessageId as TelegramMessageId

internal fun TelegramMessageId.toMessageId(): MessageId = MessageId(
    id = long.toString(),
)