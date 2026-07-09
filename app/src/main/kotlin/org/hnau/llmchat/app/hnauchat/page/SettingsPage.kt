package org.hnau.llmchat.app.hnauchat.page

import org.hnau.commons.kotlin.foldBoolean
import org.hnau.llmchat.app.chat.ButtonIcon
import org.hnau.llmchat.app.chat.ChatPage
import org.hnau.llmchat.app.chat.createButtonTitle
import org.hnau.llmchat.app.hnauchat.HnauChatProcessor

suspend fun generateSettingsPage(
    context: HnauChatProcessor.Context,
): ChatPage<HnauChatProcessor.Context> = ChatPage(
    text = "Settings",
    buttons = listOf(
        run {
            val (page, correct) = generateLLMConnectionPage(context)
            ChatPage.Button(
                id = ChatPage.Button.Id("connection"),
                title = createButtonTitle(
                    icon = correct.foldBoolean(
                        ifFalse = { ButtonIcon.error },
                        ifTrue = { ButtonIcon.success },
                    ),
                    title = "LLM connection",
                ),
                type = ChatPage.Button.Type.Child(
                    page = page,
                ),
            )
        },
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