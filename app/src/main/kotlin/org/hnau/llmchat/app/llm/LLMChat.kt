package org.hnau.llmchat.app.llm

import org.hnau.commons.gen.loggable.annotations.Loggable
import org.hnau.llmchat.app.chat.dto.ChatRequest
import org.hnau.llmchat.app.chat.dto.ChatResponse
import org.hnau.llmchat.app.db.DBAccessor
import org.hnau.llmchat.app.db.settings.UserSettings
import org.hnau.llmchat.app.db.settings.UserSettingsRepository

@Loggable
class LLMChat(
    dbAccessor: DBAccessor,
) {

    private val userSettingsRepository = UserSettingsRepository(dbAccessor)

    suspend fun handleRequest(
        request: ChatRequest,
    ): ChatResponse {
        logger.i { "Handling message from user ${request.userId}: ${request.message}" }

        return when {
            request.message == "/settings" -> handleSettings(request.userId)
            request.message.startsWith("/prompt ") -> handleSetPrompt(request.userId, request.message)
            else -> handleMessage(request)
        }
    }

    private suspend fun handleSettings(userId: org.hnau.llmchat.app.chat.dto.UserId): ChatResponse {
        val settings = userSettingsRepository.get(userId)
        val message = if (settings.basePrompt.isNotEmpty()) {
            "Текущий basePrompt: ${settings.basePrompt}"
        } else {
            "BasePrompt не установлен"
        }
        return ChatResponse(message = message)
    }

    private suspend fun handleSetPrompt(
        userId: org.hnau.llmchat.app.chat.dto.UserId,
        message: String,
    ): ChatResponse {
        val prompt = message.removePrefix("/prompt ").trim()
        if (prompt.isEmpty()) {
            return ChatResponse(message = "Использование: /prompt <текст>")
        }
        userSettingsRepository.save(userId, UserSettings(basePrompt = prompt))
        return ChatResponse(message = "BasePrompt установлен: $prompt")
    }

    private suspend fun handleMessage(request: ChatRequest): ChatResponse {
        val settings = userSettingsRepository.get(request.userId)
        logger.i { "Responding to ${request.userId} with basePrompt='${settings.basePrompt}'" }
        return ChatResponse(
            message = "You said: ${request.message}"
        )
    }
}
