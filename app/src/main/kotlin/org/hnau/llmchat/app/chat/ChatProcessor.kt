package org.hnau.llmchat.app.chat

interface ChatProcessor<C> {

    val rootPages: List<ChatRootPage<C>>

    suspend fun buildContext(
        chatId: ChatId,
    ): C

    suspend fun Chat.handleMessage(
        context: C,
        message: String,
    )
}