package org.hnau.llmchat.app.telegram.utils

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.types.IdChatIdentifier
import org.hnau.llmchat.app.chat.Chat

class TelegramChat(
    private val bot: TelegramBot,
    private val chatId: IdChatIdentifier,
) : Chat {

    override suspend fun sendMessage(
        text: String,
    ) {
        text
            .chunked(MAX_MESSAGE_LENGTH)
            .forEach { chunk ->
                bot.sendMessage(
                    chatId = chatId,
                    text = chunk,
                )
            }
    }

    companion object {
        private const val MAX_MESSAGE_LENGTH = 4096
    }
}
