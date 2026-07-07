package org.hnau.llmchat.app.telegram

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageId
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardButtons.CallbackDataInlineKeyboardButton
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import org.hnau.commons.kotlin.foldNullable

class TelegramChat(
    private val bot: TelegramBot,
    private val chatId: IdChatIdentifier,
) {

    suspend fun sendMessage(
        text: String,
        buttons: List<TelegramButton> = emptyList(),
        messageToEdit: MessageId? = null,
    ) {
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

        messageToEdit.foldNullable(
            ifNull = {
                bot.send(
                    chatId = chatId,
                    text = text,
                    replyMarkup = replyMarkup,
                )
            },
            ifNotNull = { messageId ->
                bot.editMessageText(
                    chatId = chatId,
                    messageId = messageId,
                    text = text,
                    replyMarkup = replyMarkup,
                )
            }
        )
    }
}

data class TelegramButton(
    val title: String,
    val path: CallbackDataPath,
)