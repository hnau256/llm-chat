package org.hnau.llmchat.chat.telegram.utils

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.message.HTMLParseMode
import org.hnau.llmchat.chat.api.Chat
import org.hnau.llmchat.chat.api.MessageId
import org.hnau.llmchat.chat.api.MessageToSend

class TelegramChat(
    private val bot: TelegramBot,
    private val chatId: IdChatIdentifier,
) : Chat {

    override suspend fun prepareMessages(
        mdText: String,
    ): MessageToSend {
        TODO("Not yet implemented")
    }

    override suspend fun sendMessage(
        message: MessageToSend,
    ): MessageId = bot
        .sendMessage(
            chatId = chatId,
            text = message.text,
            parseMode = HTMLParseMode,
        )
        .messageId
        .toMessageId()


    companion object {
        private const val MAX_MESSAGE_LENGTH = 4096
    }
}
