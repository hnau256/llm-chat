package org.hnau.llmchat.chat.api

interface ChatProcessor<C> {

    val rootPages: List<ChatRootPage<C>>

    suspend fun buildContext(
        chatId: ChatId,
    ): C

    suspend fun handleMessage(
        context: C,
        chat: Chat,
        transportPrompt: String,
        replayFor: TransportMessageId?,
        incomingMessageId: TransportMessageId,
        message: String,
    )
}