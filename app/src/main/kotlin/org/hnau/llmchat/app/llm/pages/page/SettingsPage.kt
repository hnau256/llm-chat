package org.hnau.llmchat.app.llm.pages.page

import org.hnau.commons.kotlin.foldNullable
import org.hnau.llmchat.app.llm.LLMChatContext
import org.hnau.llmchat.app.llm.model.name
import org.hnau.llmchat.app.telegram.CallbackDataPath
import org.hnau.llmchat.app.telegram.TelegramPageMessage

suspend fun LLMChatContext.generateSettingsPage(): TelegramPageMessage = TelegramPageMessage(
    text = "Settings",
    buttons = listOf(
        TelegramPageMessage.Button(
            id = CallbackDataPath.Entry("chooseProvider"),
            text = "Provider" + userSettings.settings.llmProviderConfig.foldNullable(
                ifNull = { "" },
                ifNotNull = { providerConfig -> " (${providerConfig.name})" },
            ),
            type = TelegramPageMessage.Button.Type.Child(
                message = generateChooseProviderPage()
            ),
        ),
        TelegramPageMessage.Button(
            id = CallbackDataPath.Entry("basePrompt"),
            text = "Base prompt",
            type = TelegramPageMessage.Button.Type.Child(
                message = generateBasePromptPage()
            ),
        ),
    ),
)