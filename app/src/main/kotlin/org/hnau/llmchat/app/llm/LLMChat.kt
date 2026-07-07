package org.hnau.llmchat.app.llm

import org.hnau.commons.gen.loggable.annotations.Loggable
import org.hnau.llmchat.app.chat.dto.ChatRequest
import org.hnau.llmchat.app.chat.dto.ChatResponse
import org.hnau.llmchat.app.db.DBAccessor

@Loggable
class LLMChat(
    dbAccessor: DBAccessor,
) {

    suspend fun handleRequest(
        request: ChatRequest,
    ): ChatResponse {
        logger.i { "Handling message from user ${request.userId}" }
        val message = request.message
        return ChatResponse(
            message = "You said: $message"
        )
    }
}