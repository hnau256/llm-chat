package org.hnau.llmchat.app.llm.pages.page

import org.hnau.llmchat.app.db.settings.update
import org.hnau.llmchat.app.llm.LLMChatContext
import org.hnau.llmchat.app.llm.model.LLMProviderConfig
import org.hnau.llmchat.app.llm.model.name
import org.hnau.llmchat.app.telegram.CallbackDataPath
import org.hnau.llmchat.app.telegram.TelegramPageMessage

suspend fun LLMChatContext.generateChooseProviderPage(): TelegramPageMessage = TelegramPageMessage(
    text = "Choose LLM provider",
    buttons = LLMProviderConfig
        .all
        .map { config ->
            TelegramPageMessage.Button(
                id = CallbackDataPath.Entry(config.name),
                text = config.name,
                type = TelegramPageMessage.Button.Type.Input(
                    onInput = { input ->
                        userSettings.update { copy(basePrompt = input) }
                    }
                )
            )
        },
)