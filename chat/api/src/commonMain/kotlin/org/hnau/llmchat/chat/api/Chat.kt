package org.hnau.llmchat.chat.api

interface Chat {

    suspend fun prepareMessages(
        mdText: String,
    ): MessageToSend

    suspend fun sendMessage(
        message: MessageToSend,
    ): MessageId
}