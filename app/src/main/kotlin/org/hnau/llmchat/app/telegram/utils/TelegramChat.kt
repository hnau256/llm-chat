package org.hnau.llmchat.app.telegram.utils

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.types.IdChatIdentifier
import org.hnau.llmchat.chat.api.Chat
import org.hnau.llmchat.chat.api.MessageId

class TelegramChat(
    private val bot: TelegramBot,
    private val chatId: IdChatIdentifier,
) : Chat {

    override suspend fun sendMessage(
        text: String,
    ): List<MessageId> = text
        .chunked(MAX_MESSAGE_LENGTH)
        .map { chunk ->
            bot
                .sendMessage(
                    chatId = chatId,
                    text = chunk,
                )
                .messageId
                .toMessageId()
        }


    companion object {
        private const val MAX_MESSAGE_LENGTH = 4096
    }
}
