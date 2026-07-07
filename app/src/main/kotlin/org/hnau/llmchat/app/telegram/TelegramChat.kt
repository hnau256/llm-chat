package org.hnau.llmchat.app.telegram

import dev.inmo.tgbotapi.types.MessageId

interface TelegramChat {

    suspend fun sendMessage(
        text: String,
        buttons: List<TelegramButton> = emptyList(),
        messageToEdit: MessageId? = null,
    )
}

data class TelegramButton(
    val title: String,
    val path: CallbackDataPath,
)