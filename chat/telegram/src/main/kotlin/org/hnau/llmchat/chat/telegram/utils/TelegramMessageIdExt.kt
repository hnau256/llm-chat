package org.hnau.llmchat.chat.telegram.utils

import org.hnau.llmchat.chat.api.TransportMessageId
import dev.inmo.tgbotapi.types.MessageId as TelegramMessageId

internal fun TelegramMessageId.toMessageId(): TransportMessageId = TransportMessageId(
    id = long.toString(),
)