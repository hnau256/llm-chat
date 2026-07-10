package org.hnau.llmchat.chat.api

data class ChatRootPage<C>(
    val id: ChatPage.Button.Id,
    val title: String,
    val generatePage: suspend (context: C) -> ChatPage<C>,
)