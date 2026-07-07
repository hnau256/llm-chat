package org.hnau.llmchat.app.telegram

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.deleteMessage
import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageId
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.CallbackDataInlineKeyboardButton
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import org.hnau.commons.kotlin.foldNullable

class TelegramChat(
    val bot: TelegramBot,
    val id: IdChatIdentifier,
) {

    suspend fun sendMessage(
        text: String,
        buttons: List<TelegramButton> = emptyList(),
        messageToEdit: MessageId? = null,
    ): MessageId {
        val replyMarkup = InlineKeyboardMarkup(
            keyboard = buttons.map { button ->
                listOf(
                    CallbackDataInlineKeyboardButton(
                        text = button.title,
                        callbackData = button.path.encode(),
                    )
                )
            }
        )

        return messageToEdit.foldNullable(
            ifNull = {
                bot.send(
                    chatId = id,
                    text = text,
                    replyMarkup = replyMarkup,
                ).messageId
            },
            ifNotNull = { messageId ->
                bot.editMessageText(
                    chatId = id,
                    messageId = messageId,
                    text = text,
                    replyMarkup = replyMarkup,
                )
                messageId
            }
        )
    }

    suspend fun deleteMessage(
        messageId: MessageId,
    ) {
        bot.deleteMessage(
            chatId = id,
            messageId = messageId,
        )
    }
}

data class TelegramButton(
    val title: String,
    val path: CallbackDataPath,
)
