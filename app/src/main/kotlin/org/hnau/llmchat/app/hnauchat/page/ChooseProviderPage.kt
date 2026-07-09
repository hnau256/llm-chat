package org.hnau.llmchat.app.hnauchat.page

import org.hnau.commons.kotlin.foldBoolean
import org.hnau.llmchat.app.chat.ButtonResult
import org.hnau.llmchat.app.chat.ChatPage
import org.hnau.llmchat.app.hnauchat.HnauChatProcessor
import org.hnau.llmchat.app.hnauchat.settings.update
import org.hnau.llmchat.app.llm.model.LLMProviderType
import org.hnau.llmchat.app.llm.model.createBaseConfig

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
                    onClick = { context ->
                        context.settings.update {
                            (llmClientConfig?.type == type).foldBoolean(
                                ifTrue = { this },
                                ifFalse = {
                                    copy(
                                        llmClientConfig = type.createBaseConfig(),
                                    )
                                }
                            )
                        }
                        ButtonResult.navigateBack
                    }
                )
            )
        },
)