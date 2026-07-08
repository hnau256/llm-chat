package org.hnau.llmchat.app.llm.pages.page

import org.hnau.commons.kotlin.foldBoolean
import org.hnau.commons.kotlin.foldNullable
import org.hnau.llmchat.app.llm.LLMChatContext
import org.hnau.llmchat.app.llm.model.name
import org.hnau.llmchat.app.telegram.CallbackDataPath
import org.hnau.llmchat.app.telegram.TelegramPageMessage

suspend fun LLMChatContext.generateSettingsPage(): TelegramPageMessage = TelegramPageMessage(
    text = "Settings",
    buttons = buildList {

        val llmProviderConfig = userSettings.settings.llmProviderConfig

        add(
            TelegramPageMessage.Button(
                id = CallbackDataPath.Entry("chooseProvider"),
                text = "Provider" + llmProviderConfig.foldNullable(
                    ifNull = { "" },
                    ifNotNull = { providerConfig ->
                        val correct = providerConfig.tryCreateConfig() != null
                        val correctSuffix = correct.foldBoolean(
                            ifFalse = { "❌" },
                            ifTrue = { "✅" }
                        )
                        " (${providerConfig.name} $correctSuffix)"
                    },
                ),
                type = TelegramPageMessage.Button.Type.Child(
                    message = generateChooseProviderPage()
                ),
            )
        )

        llmProviderConfig?.let { config ->
            addAll(
                generateLLMProviderConfigButtons(
                    config = config,
                )
            )
        }

        add(
            TelegramPageMessage.Button(
                id = CallbackDataPath.Entry("basePrompt"),
                text = "Base prompt",
                type = TelegramPageMessage.Button.Type.Child(
                    message = generateBasePromptPage()
                ),
            )
        )
    },
)