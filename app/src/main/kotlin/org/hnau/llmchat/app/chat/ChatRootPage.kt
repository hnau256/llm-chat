package org.hnau.llmchat.app.chat

data class ChatRootPage<C>(
    val title: String,
    val generatePage: suspend (context: C) -> ChatPage<C>,
    val id: ChatPage.Button.Id = ChatPage.Button.Id.generate(title),
)