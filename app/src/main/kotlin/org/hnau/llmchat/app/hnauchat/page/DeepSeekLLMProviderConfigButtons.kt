package org.hnau.llmchat.app.hnauchat.page

import org.hnau.llmchat.app.chat.ButtonIcon
import org.hnau.llmchat.app.chat.ButtonResult
import org.hnau.llmchat.app.chat.ChatPage
import org.hnau.llmchat.app.chat.createButtonTitle
import org.hnau.llmchat.app.hnauchat.settings.update
import org.hnau.llmchat.app.dto.ApiKey
import org.hnau.llmchat.app.hnauchat.HnauChatProcessor
import org.hnau.llmchat.app.llm.model.LLMClientConfig

suspend fun generateDeepSeekLLMProviderConfigButtons(
    context: HnauChatProcessor.Context,
    config: LLMClientConfig.DeepSeek,
): List<ChatPage.Button<HnauChatProcessor.Context>> = listOf(
    ChatPage.Button(
        id = ChatPage.Button.Id("apiKey"),
        title = createButtonTitle(
            icon = ButtonIcon.key,
            title = "Api key",
            additionalInfo = config.apiKey?.let { "+" },
        ),
        type = ChatPage.Button.Type.Input { context, input ->
            context.settings.update {
                copy(
                    llmClientConfig = config.copy(
                        apiKey = ApiKey.tryCreate(input),
                    )
                )
            }
            ButtonResult.noNavigate
        }
    )
)