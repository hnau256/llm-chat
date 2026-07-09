package org.hnau.llmchat.app.hnauchat.page

import org.hnau.llmchat.app.chat.ButtonIcon
import org.hnau.llmchat.app.chat.ChatPage
import org.hnau.llmchat.app.chat.createButtonTitle
import org.hnau.llmchat.app.hnauchat.HnauChatProcessor
import org.hnau.llmchat.app.llm.model.name

suspend fun generateLLMConnectionPage(
    context: HnauChatProcessor.Context,
): Pair<ChatPage<HnauChatProcessor.Context>, Boolean> {

    val llmClientConfig = context.settings.settings.llmClientConfig

    val correct = llmClientConfig?.tryCreateLLMClient() != null

    val page = ChatPage(
        text = "LLM connection",
        buttons = buildList {

            add(
                ChatPage.Button(
                    id = ChatPage.Button.Id("chooseProvider"),
                    title = createButtonTitle(
                        icon = ButtonIcon.language,
                        title = "Provider",
                        additionalInfo = llmClientConfig?.name,
                    ),
                    type = ChatPage.Button.Type.Child(
                        page = generateChooseProviderPage(context)
                    ),
                )
            )

            llmClientConfig?.let { config ->
                addAll(
                    generateLLMProviderConfigButtons(
                        context = context,
                        config = config,
                    )
                )
            }
        }
    )

    return page to correct
}