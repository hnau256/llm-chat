package org.hnau.llmchat.app.hnauchat.page

import org.hnau.commons.kotlin.ifTrue
import org.hnau.llmchat.app.chat.ButtonIcon
import org.hnau.llmchat.app.chat.ButtonResult
import org.hnau.llmchat.app.chat.ChatPage
import org.hnau.llmchat.app.chat.createButtonTitle
import org.hnau.llmchat.app.hnauchat.HnauChatProcessor
import org.hnau.llmchat.app.hnauchat.llmconnection.LLMConnectionManager

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