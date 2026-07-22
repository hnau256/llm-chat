package org.hnau.llmchat.chat.telegram.utils

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.message.HTMLParseMode
import org.hnau.llmchat.chat.api.Chat
import org.hnau.llmchat.chat.api.ChatMessageId
import org.hnau.llmchat.chat.telegram.utils.md.mdToTGMessages

class TelegramChat(
    private val bot: TelegramBot,
    private val chatId: IdChatIdentifier,
) : Chat {

    override suspend fun sendMessage(
        markdownText: String,
    ): List<ChatMessageId> = markdownText
        .mdToTGMessages()
        ?.map { message ->
            bot
                .sendMessage(
                    chatId = chatId,
                    text = message,
                    parseMode = HTMLParseMode,
                )
                .messageId
                .toMessageId()
        }
        .orEmpty()
}
