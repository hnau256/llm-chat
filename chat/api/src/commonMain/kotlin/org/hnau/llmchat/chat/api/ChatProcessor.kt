package org.hnau.llmchat.chat.api

interface ChatProcessor<C> {

    val rootPages: List<ChatRootPage<C>>

    suspend fun buildContext(
        chatId: ChatId,
    ): C

    suspend fun Chat.handleMessage(
        context: C,
        transportPrompt: String,
        replayFor: MessageId?,
        message: String,
    )
}