package org.hnau.llmchat.app.hnauchat.page

import org.hnau.llmchat.chat.api.ButtonIcon
import org.hnau.llmchat.chat.api.ButtonResult
import org.hnau.llmchat.chat.api.ChatPage
import org.hnau.llmchat.chat.api.createButtonTitle
import org.hnau.llmchat.app.hnauchat.settings.update
import org.hnau.llmchat.app.hnauchat.HnauChatProcessor
import kotlin.collections.listOf

suspend fun generateBasePromptPage(
    context: HnauChatProcessor.Context,
): ChatPage<HnauChatProcessor.Context> = ChatPage(
    text = "Base prompt: ${context.settings.settings.basePrompt}",
    buttons = listOf(
        ChatPage.Button(
            id = ChatPage.Button.Id("edit"),
            title = createButtonTitle(
                icon = ButtonIcon.edit,
                title = "Edit",
            ),
            type = ChatPage.Button.Type.Input(
                onInput = { input ->
                    context.settings.update { copy(basePrompt = input) }
                    ButtonResult.noNavigate
                }
            )
        )
    ),
)