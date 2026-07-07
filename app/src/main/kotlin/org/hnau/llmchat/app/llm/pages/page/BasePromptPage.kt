package org.hnau.llmchat.app.llm.pages.page

import org.hnau.llmchat.app.db.settings.update
import org.hnau.llmchat.app.llm.LLMChatContext
import org.hnau.llmchat.app.telegram.ButtonResult
import org.hnau.llmchat.app.telegram.CallbackDataPath
import org.hnau.llmchat.app.telegram.TelegramPageMessage

suspend fun LLMChatContext.generateBasePromptPage(): TelegramPageMessage = TelegramPageMessage(
    text = "Base prompt: ${userSettings.settings.basePrompt}",
    buttons = listOf(
        TelegramPageMessage.Button(
            id = CallbackDataPath.Entry("edit"),
            text = "Edit",
            type = TelegramPageMessage.Button.Type.Input(
                onInput = { input ->
                    userSettings.update { copy(basePrompt = input) }
                    ButtonResult.noNavigate
                }
            )
        )
    ),
)