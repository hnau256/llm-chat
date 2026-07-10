package org.hnau.llmchat.chat.api

interface Chat {

    suspend fun sendMessage(
        text: String,
    ): List<MessageId>
}