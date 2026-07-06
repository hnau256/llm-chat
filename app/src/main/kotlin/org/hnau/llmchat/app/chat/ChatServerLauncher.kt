package org.hnau.llmchat.app.chat

import org.hnau.llmchat.app.chat.dto.ChatRequest
import org.hnau.llmchat.app.chat.dto.ChatResponse

interface ChatServerLauncher {

    suspend fun launchChatServer(
        callback: suspend (ChatRequest) -> ChatResponse,
    )

    companion object {

        fun create(
            launchChatServer: suspend (
                callback: suspend (ChatRequest) -> ChatResponse,
            ) -> Unit
        ): ChatServerLauncher = object : ChatServerLauncher {

            override suspend fun launchChatServer(
                callback: suspend (ChatRequest) -> ChatResponse,
            ) {
                launchChatServer(callback)
            }

        }
    }
}