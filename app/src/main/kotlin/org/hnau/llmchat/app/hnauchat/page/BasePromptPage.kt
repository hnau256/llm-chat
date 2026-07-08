package org.hnau.llmchat.app.hnauchat.page

import org.hnau.llmchat.app.chat.ButtonIcon
import org.hnau.llmchat.app.chat.ButtonResult
import org.hnau.llmchat.app.chat.ChatPage
import org.hnau.llmchat.app.chat.createButtonTitle
import org.hnau.llmchat.app.db.settings.update
import org.hnau.llmchat.app.hnauchat.HnauChatProcessor
import kotlin.collections.listOf

suspend fun generateBasePromptPage(
    context: HnauChatProcessor.Context,
): ChatPage<HnauChatProcessor.Context> = ChatPage(
    text = "Base prompt: ${context.settings.settings.basePrompt}",
    buttons = listOf(
        ChatPage.Button(
            title = createButtonTitle(
                icon = ButtonIcon.edit,
                title = "Edit",
            ),
            type = ChatPage.Button.Type.Input(
                onInput = { context, input ->
                    context.settings.update { copy(basePrompt = input) }
                    ButtonResult.noNavigate
                }
            )
        )
    ),
)