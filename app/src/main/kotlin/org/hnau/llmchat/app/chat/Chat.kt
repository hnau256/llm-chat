package org.hnau.llmchat.app.chat

interface Chat {

    suspend fun sendMessage(
        text: String,
    ): List<MessageId>
}