package org.hnau.llmchat.app.hnauchat.messages

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import org.hnau.llmchat.app.hnauchat.HnauChatProcessor

private const val SYSTEM_PROMPT =
    "You are a helpful AI assistant. Keep responses concise and to the point. Reply in the same language as the user's message."

internal suspend fun buildLLMChatMessages(
    transportPrompt: String,
    context: HnauChatProcessor.Context,
    historyMessages: List<MessageRecord>,
    userMessage: MessageRecord?,
    summary: String? = null,
): List<Message> = buildList {

    add(
        Message.System(
            content = listOfNotNull(
                SYSTEM_PROMPT,
                transportPrompt,
                context
                    .settings
                    .settings
                    .basePrompt
                    .takeIf(String::isNotEmpty)
            ).joinToString(
                separator = "\n\n",
            ),
            metaInfo = RequestMetaInfo.Empty,
        )
    )

    if (summary != null) {
        add(
            Message.System(
                content = "Previous conversation summary:\n$summary",
                metaInfo = RequestMetaInfo.Empty,
            )
        )
    }

    addAll(historyMessages.map { it.koogMessage })

    if (userMessage != null) {
        add(userMessage.koogMessage)
    }
}

private val MessageRecord.koogMessage: Message
    get() = role.fold(
        ifUser = {
            Message.User(
                content = text,
                metaInfo = RequestMetaInfo(timestamp),
            )
        },
        ifAssistant = {
            Message.Assistant(
                content = text,
                metaInfo = ResponseMetaInfo(timestamp),
            )
        },
    )