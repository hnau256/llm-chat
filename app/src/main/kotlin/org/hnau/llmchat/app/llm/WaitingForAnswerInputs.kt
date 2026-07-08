package org.hnau.llmchat.app.llm

import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.MessageId
import org.hnau.llmchat.app.telegram.utils.CallbackDataPath
import java.util.concurrent.ConcurrentHashMap

data class WaitingInput(
    val path: CallbackDataPath,
    val promptMessageId: MessageId,
)

internal class WaitingForAnswerInputs {

    private val inputs = ConcurrentHashMap<IdChatIdentifier, WaitingInput>()

    data class InChat(
        val add: (inputPath: CallbackDataPath, promptMessageId: MessageId) -> Unit,
        val consume: () -> WaitingInput?,
    )

    fun forChat(
        chatId: IdChatIdentifier,
    ): InChat = InChat(
        add = { path, promptMessageId ->
            inputs[chatId] = WaitingInput(path, promptMessageId)
        },
        consume = { inputs.remove(chatId) }
    )
}
