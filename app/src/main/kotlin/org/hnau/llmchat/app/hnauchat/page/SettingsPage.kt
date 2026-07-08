package org.hnau.llmchat.app.hnauchat.page

import org.hnau.commons.kotlin.foldBoolean
import org.hnau.commons.kotlin.foldNullable
import org.hnau.llmchat.app.chat.ChatPage
import org.hnau.llmchat.app.hnauchat.HnauChatProcessor
import org.hnau.llmchat.app.llm.model.name

suspend fun generateSettingsPage(
    context: HnauChatProcessor.Context,
): ChatPage<HnauChatProcessor.Context> = ChatPage(
    text = "Settings",
    buttons = buildList {

        val llmProviderConfig = context.settings.settings.llmClientConfig

        add(
            ChatPage.Button(
                id = ChatPage.Button.Id("chooseProvider"),
                title = "Provider" + llmProviderConfig.foldNullable(
                    ifNull = { "" },
                    ifNotNull = { providerConfig ->
                        val correct = providerConfig.tryCreateLLMClient() != null
                        val correctSuffix = correct.foldBoolean(
                            ifFalse = { "❌" },
                            ifTrue = { "✅" }
                        )
                        " (${providerConfig.name} $correctSuffix)"
                    },
                ),
                type = ChatPage.Button.Type.Child(
                    message = generateChooseProviderPage(context)
                ),
            )
        )

        llmProviderConfig?.let { config ->
            addAll(
                generateLLMProviderConfigButtons(
                    context = context,
                    config = config,
                )
            )
        }

        add(
            ChatPage.Button(
                id = ChatPage.Button.Id("basePrompt"),
                title = "Base prompt",
                type = ChatPage.Button.Type.Child(
                    message = generateBasePromptPage(
                        context = context,
                    )
                ),
            )
        )
    },
)