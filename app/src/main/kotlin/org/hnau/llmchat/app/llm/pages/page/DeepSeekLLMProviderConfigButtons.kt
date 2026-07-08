package org.hnau.llmchat.app.llm.pages.page

import org.hnau.commons.kotlin.foldNullable
import org.hnau.llmchat.app.db.settings.update
import org.hnau.llmchat.app.dto.ApiKey
import org.hnau.llmchat.app.llm.LLMChatContext
import org.hnau.llmchat.app.llm.model.LLMProviderConfig
import org.hnau.llmchat.app.telegram.ButtonResult
import org.hnau.llmchat.app.telegram.CallbackDataPath
import org.hnau.llmchat.app.telegram.TelegramPageMessage

suspend fun LLMChatContext.generateDeepSeekLLMProviderConfigButtons(
    config: LLMProviderConfig.DeepSeek,
): List<TelegramPageMessage.Button> = listOf(
    TelegramPageMessage.Button(
        id = CallbackDataPath.Entry("apiKey"),
        text = "Api key" + config.apiKey.foldNullable(
            ifNull = { "" },
            ifNotNull = { " [+]" }
        ),
        type = TelegramPageMessage.Button.Type.Input { input ->
            userSettings.update {
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