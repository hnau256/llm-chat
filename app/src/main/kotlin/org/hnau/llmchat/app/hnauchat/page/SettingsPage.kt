package org.hnau.llmchat.app.hnauchat.page

import org.hnau.commons.kotlin.foldNullable
import org.hnau.llmchat.app.hnauchat.HnauChatProcessor
import org.hnau.llmchat.chat.api.ButtonIcon
import org.hnau.llmchat.chat.api.ChatPage
import org.hnau.llmchat.chat.api.createButtonTitle

suspend fun generateSettingsPage(
    context: HnauChatProcessor.Context,
): ChatPage<HnauChatProcessor.Context> = ChatPage(
    text = "Settings",
    buttons = listOf(
        ChatPage.Button(
            id = ChatPage.Button.Id("connection"),
            title = createButtonTitle(
                icon = context
                    .llmConnectionManager
                    .client
                    ?.getClientWithModel()
                    .foldNullable(
                        ifNull = { ButtonIcon.error },
                        ifNotNull = { ButtonIcon.success }
                    ),
                title = "LLM connection",
            ),
            type = ChatPage.Button.Type.Child(
                page = generateLLMConnectionPage(context),
            ),
        ),
        ChatPage.Button(
            id = ChatPage.Button.Id("basePrompt"),
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
                page = generateBasePromptPage(
                    context = context,
                )
            ),
        )
    ),
)