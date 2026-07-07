package org.hnau.llmchat.app.llm

import dev.inmo.tgbotapi.types.IdChatIdentifier
import org.hnau.llmchat.app.telegram.CallbackDataPath
import java.util.concurrent.ConcurrentHashMap

internal class WaitingForAnswerInputs {

    private val inputs = ConcurrentHashMap<IdChatIdentifier, CallbackDataPath>()


    data class InChat(
        val add: (inputPath: CallbackDataPath) -> Unit,
        val consume: () -> CallbackDataPath?,
    )

    fun forChat(
        chatId: IdChatIdentifier,
    ): InChat = InChat(
        add = { path -> inputs[chatId] = path },
        consume = { inputs.remove(chatId) }
    )
}