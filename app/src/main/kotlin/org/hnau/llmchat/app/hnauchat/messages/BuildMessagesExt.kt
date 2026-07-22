package org.hnau.llmchat.app.hnauchat.messages

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import org.hnau.llmchat.app.hnauchat.HnauChatProcessor
import kotlin.time.Clock

private const val SYSTEM_PROMPT =
    "You are a helpful AI assistant. Keep responses concise and to the point. Reply in the same language as the user's message."

internal suspend fun buildLLMChatMessages(
    transportPrompt: String,
    context: HnauChatProcessor.Context,
    parentMessageId: StorageMessageId?,
    userMessage: String,
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

    addAll(
        parentMessageId
            ?.let { id -> context.messagesRepository.getHistory(id) }
            .orEmpty().map { historyRecord ->
                historyRecord
                    .role
                    .fold(
                        ifUser = {
                            Message.User(
                                content = historyRecord.text,
                                metaInfo = RequestMetaInfo(historyRecord.timestamp),
                            )
                        },
                        ifAssistant = {
                            Message.Assistant(
                                content = historyRecord.text,
                                metaInfo = ResponseMetaInfo(historyRecord.timestamp),
                            )
                        },
                    )
            }
    )

    add(
        Message.User(
            content = userMessage,
            metaInfo = RequestMetaInfo.create { Clock.System.now() },
        )
    )
}