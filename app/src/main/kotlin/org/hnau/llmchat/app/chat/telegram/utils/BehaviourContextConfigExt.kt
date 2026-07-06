package org.hnau.llmchat.app.chat.telegram.utils

import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onText
import org.hnau.llmchat.app.chat.dto.ChatRequest
import org.hnau.llmchat.app.chat.dto.ChatResponse
import org.hnau.llmchat.app.chat.dto.UserId

internal fun BehaviourContext.config(
    callback: suspend (ChatRequest) -> ChatResponse,
) {
    onText { textMessage ->
        val request = ChatRequest(
            userId = textMessage.chat.id.chatId.toString().let(::UserId),
            message = textMessage.content.text,
        )
        val response = callback(request)
        reply(
            to = textMessage,
            text = response.message,
        )
    }
}