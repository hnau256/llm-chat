package org.hnau.llmchat.app.hnauchat.page

import org.hnau.commons.kotlin.it
import org.hnau.llmchat.chat.api.ButtonIcon
import org.hnau.llmchat.chat.api.ButtonResult
import org.hnau.llmchat.chat.api.ChatPage
import org.hnau.llmchat.chat.api.createButtonTitle
import org.hnau.llmchat.app.hnauchat.HnauChatProcessor
import org.hnau.llmchat.app.hnauchat.llmconnection.fold
import org.hnau.llmchat.app.llm.model.name

suspend fun generateLLMConnectionPage(
    context: HnauChatProcessor.Context,
): ChatPage<HnauChatProcessor.Context> = ChatPage(
    text = "LLM connection",
    buttons = buildList {

        add(
            ChatPage.Button(
                id = ChatPage.Button.Id("chooseProvider"),
                title = createButtonTitle(
                    icon = ButtonIcon.language,
                    title = "Provider",
                    additionalInfo = context
                        .llmConnectionManager
                        .config
                        ?.name,
                ),
                type = ChatPage.Button.Type.Child(
                    page = generateChooseProviderPage(context)
                ),
            )
        )

        addAll(
            context
                .llmConnectionManager
                .configFields
                .map { field ->
                    ChatPage.Button(
                        id = ChatPage.Button.Id(field.id),
                        title = createButtonTitle(
                            icon = field.icon,
                            title = field.title,
                            additionalInfo = field.content.fold(
                                ifNoValue = { null },
                                ifSensitive = { "+" },
                                ifValue = ::it,
                            ),
                        ),
                        type = ChatPage.Button.Type.Input { input ->
                            field.set(input)
                            ButtonResult.noNavigate
                        }
                    )
                }
        )

        context
            .llmConnectionManager
            .client
            ?.let { client ->

                add(
                    ChatPage.Button(
                        id = ChatPage.Button.Id("model"),
                        title = createButtonTitle(
                            icon = ButtonIcon.feedback,
                            title = "Model",
                            additionalInfo = client
                                .getClientWithModel()
                                ?.value
                                ?.id,
                        ),
                        type = ChatPage.Button.Type.Child(
                            page = generateChooseModelPage(
                                context = context,
                                client = client,
                            )
                        ),
                    )
                )
            }
    }
)