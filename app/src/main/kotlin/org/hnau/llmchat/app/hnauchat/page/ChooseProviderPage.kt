package org.hnau.llmchat.app.hnauchat.page

import org.hnau.llmchat.app.hnauchat.HnauChatProcessor
import org.hnau.llmchat.app.llm.model.LLMProviderType
import org.hnau.llmchat.chat.api.ButtonResult
import org.hnau.llmchat.chat.api.ChatPage

suspend fun generateChooseProviderPage(
    context: HnauChatProcessor.Context,
): ChatPage<HnauChatProcessor.Context> = ChatPage(
    text = "Choose LLM provider",
    buttons = LLMProviderType
        .entries
        .map { type ->
            ChatPage.Button(
                id = ChatPage.Button.Id(type.name.lowercase()),
                title = type.name,
                type = ChatPage.Button.Type.Click(
                    onClick = {
                        context
                            .llmConnectionManager
                            .selectType(type)
                        ButtonResult.navigateBack
                    }
                )
            )
        },
)