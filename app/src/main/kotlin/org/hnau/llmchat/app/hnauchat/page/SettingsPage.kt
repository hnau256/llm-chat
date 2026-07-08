package org.hnau.llmchat.app.hnauchat.page

import org.hnau.commons.kotlin.foldBoolean
import org.hnau.commons.kotlin.foldNullable
import org.hnau.llmchat.app.chat.ButtonIcon
import org.hnau.llmchat.app.chat.ChatPage
import org.hnau.llmchat.app.chat.createButtonTitle
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
                title = run {
                    val correct = llmProviderConfig?.tryCreateLLMClient() != null
                    createButtonTitle(
                        icon = correct.foldBoolean(
                            ifFalse = { ButtonIcon.error },
                            ifTrue = { ButtonIcon.success },
                        ),
                        title = "Provider",
                        additionalInfo = llmProviderConfig?.name,
                    )
                },
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
                title = createButtonTitle(
                    icon = ButtonIcon.rules,
                    title = "Base prompt",
                    additionalInfo = context
                        .settings
                        .settings
                        .basePrompt
                        .takeIf(String::isNotEmpty)
                        ?.let { "+" },
                ),
                type = ChatPage.Button.Type.Child(
                    message = generateBasePromptPage(
                        context = context,
                    )
                ),
            )
        )
    },
)