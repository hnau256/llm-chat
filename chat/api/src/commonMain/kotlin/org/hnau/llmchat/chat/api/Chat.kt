package org.hnau.llmchat.chat.api

interface Chat {

    suspend fun sendMessage(
        markdownText: String,
    ): List<MessageId>
}