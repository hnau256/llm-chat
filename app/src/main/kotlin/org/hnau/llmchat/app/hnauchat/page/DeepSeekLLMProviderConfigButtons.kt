package org.hnau.llmchat.app.hnauchat.page

import org.hnau.commons.kotlin.foldNullable
import org.hnau.llmchat.app.chat.ButtonResult
import org.hnau.llmchat.app.chat.ChatPage
import org.hnau.llmchat.app.db.settings.update
import org.hnau.llmchat.app.dto.ApiKey
import org.hnau.llmchat.app.hnauchat.HnauChatProcessor
import org.hnau.llmchat.app.llm.model.LLMProviderConfig

suspend fun generateDeepSeekLLMProviderConfigButtons(
    context: HnauChatProcessor.Context,
    config: LLMProviderConfig.DeepSeek,
): List<ChatPage.Button<HnauChatProcessor.Context>> = listOf(
    ChatPage.Button(
        id = ChatPage.Button.Id("apiKey"),
        title = "Api key" + config.apiKey.foldNullable(
            ifNull = { "" },
            ifNotNull = { " [+]" }
        ),
        type = ChatPage.Button.Type.Input { context, input ->
            context.settings.update {
                copy(
                    llmProviderConfig = config.copy(
                        apiKey = ApiKey.tryCreate(input),
                    )
                )
            }
            ButtonResult.noNavigate
        }
    )
)