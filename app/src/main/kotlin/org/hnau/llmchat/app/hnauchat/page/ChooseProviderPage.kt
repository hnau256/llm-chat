package org.hnau.llmchat.app.hnauchat.page

import org.hnau.llmchat.app.chat.ButtonResult
import org.hnau.llmchat.app.chat.ChatPage
import org.hnau.llmchat.app.db.settings.update
import org.hnau.llmchat.app.hnauchat.HnauChatProcessor
import org.hnau.llmchat.app.llm.model.LLMProviderConfig
import org.hnau.llmchat.app.llm.model.name

suspend fun generateChooseProviderPage(
    context: HnauChatProcessor.Context,
): ChatPage<HnauChatProcessor.Context> = ChatPage(
    text = "Choose LLM provider",
    buttons = LLMProviderConfig
        .all
        .map { config ->
            ChatPage.Button(
                title = config.name,
                type = ChatPage.Button.Type.Click(
                    onClick = { context ->
                        context.settings.update {
                            copy(
                                llmProviderConfig = config,
                            )
                        }
                        ButtonResult.navigateBack
                    }
                )
            )
        },
)