package org.hnau.llmchat.app.hnauchat.page

import org.hnau.commons.kotlin.ifTrue
import org.hnau.llmchat.app.hnauchat.HnauChatProcessor
import org.hnau.llmchat.app.hnauchat.llmconnection.LLMConnectionManager
import org.hnau.llmchat.chat.api.ButtonIcon
import org.hnau.llmchat.chat.api.ButtonResult
import org.hnau.llmchat.chat.api.ChatPage
import org.hnau.llmchat.chat.api.createButtonTitle

suspend fun generateChooseModelPage(
    context: HnauChatProcessor.Context,
    client: LLMConnectionManager.Client,
): ChatPage<HnauChatProcessor.Context> = ChatPage(
    text = "Choose LLM model",
    buttons = client.getModels().map { model ->
        ChatPage.Button(
            id = ChatPage.Button.Id(model.model.id),
            title = createButtonTitle(
                title = model.model.id,
                icon = model.selected.ifTrue { ButtonIcon.success },
            ),
            type = ChatPage.Button.Type.Click(
                onClick = {
                    client.setModel(model.model)
                    ButtonResult.navigateBack
                }
            )
        )
    },
)