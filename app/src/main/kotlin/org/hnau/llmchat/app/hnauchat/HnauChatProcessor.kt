package org.hnau.llmchat.app.hnauchat

import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import org.hnau.commons.gen.pipe.annotations.Pipe
import org.hnau.llmchat.app.db.DBAccessor
import org.hnau.llmchat.app.hnauchat.llmconnection.LLMConnectionManager
import org.hnau.llmchat.app.hnauchat.messages.MessageRecord
import org.hnau.llmchat.app.hnauchat.messages.MessageRole
import org.hnau.llmchat.app.hnauchat.messages.MessagesRepository
import org.hnau.llmchat.app.hnauchat.page.generateSettingsPage
import org.hnau.llmchat.app.hnauchat.settings.UserSettingsRepository
import org.hnau.llmchat.app.hnauchat.utils.ModelsProvider
import org.hnau.llmchat.chat.api.Chat
import org.hnau.llmchat.chat.api.ChatId
import org.hnau.llmchat.chat.api.ChatPage
import org.hnau.llmchat.chat.api.ChatProcessor
import org.hnau.llmchat.chat.api.ChatRootPage
import org.hnau.llmchat.chat.api.MessageId
import kotlin.time.Clock

class HnauChatProcessor(
    private val dependencies: Dependencies,
) : ChatProcessor<HnauChatProcessor.Context> {

    @Pipe
    interface Dependencies {

        val db: DBAccessor

        val modelsProvider: ModelsProvider

        fun llmConnectionManager(
            settings: UserSettingsRepository,
        ): LLMConnectionManager.Dependencies

        companion object
    }

    data class Context(
        val chatId: ChatId,
        val messagesRepository: MessagesRepository,
        val settings: UserSettingsRepository,
        val llmConnectionManager: LLMConnectionManager,
    )

    override val rootPages: List<ChatRootPage<Context>> = listOf(
        ChatRootPage(
            id = ChatPage.Button.Id("settings"),
            title = "Settings",
            generatePage = { context ->
                generateSettingsPage(context)
            }
        )
    )

    override suspend fun buildContext(
        chatId: ChatId,
    ): Context {

        val settings = UserSettingsRepository.create(
            db = dependencies.db,
            chatId = chatId,
        )

        return Context(
            chatId = chatId,
            messagesRepository = MessagesRepository.create(
                db = dependencies.db,
            ),
            settings = settings,
            llmConnectionManager = LLMConnectionManager(
                dependencies = dependencies.llmConnectionManager(
                    settings = settings,
                )
            )
        )
    }

    override suspend fun Chat.handleMessage(
        context: Context,
        transportPrompt: String,
        replayFor: MessageId?,
        incomingMessageId: MessageId,
        message: String
    ) {

        val parentDbId = if (replayFor != null) {
            context.messagesRepository.findByTransportId(
                userId = context.chatId,
                transportId = replayFor,
            ) ?: run {
                sendMessage("Unable to find the message you replied to")
                return
            }
        } else {
            context.messagesRepository.findLastMessageId(
                userId = context.chatId,
            )
        }

        val userMsgId = MessageId.new()

        context.messagesRepository.save(
            MessageRecord(
                id = userMsgId,
                userId = context.chatId,
                role = MessageRole.User,
                transportIds = listOf(incomingMessageId),
                text = message,
                timestamp = Clock.System.now(),
                parentMessageId = parentDbId,
                summary = null,
            )
        )

        val historyMessages = parentDbId?.let { id ->
            context.messagesRepository.findById(id)
        }

        val (client, model) = context
            .llmConnectionManager
            .client
            ?.getClientWithModel()
            ?: run {
                sendMessage("Configure LLM connection before sending messages")
                return
            }

        val response = runCatching {
            client.execute(
                prompt = Prompt(
                    messages = listOfNotNull(
                        Message.System(
                            content = "You are a helpful AI assistant. Keep responses concise and to the point. Reply in the same language as the user's message.",
                            metaInfo = RequestMetaInfo.Empty,
                        ),
                        Message.System(
                            content = transportPrompt,
                            metaInfo = RequestMetaInfo.Empty,
                        ),
                        context
                            .settings
                            .settings
                            .basePrompt
                            .takeIf(String::isNotEmpty)
                            ?.let { basePrompt ->
                                Message.User(
                                    content = basePrompt,
                                    metaInfo = RequestMetaInfo.Empty,
                                )
                            },
                        historyMessages?.let { historyRecord ->
                            when (historyRecord.role) {
                                MessageRole.User -> Message.User(
                                    content = historyRecord.text,
                                    metaInfo = RequestMetaInfo(historyRecord.timestamp),
                                )
                                MessageRole.Assistant -> Message.Assistant(
                                    content = historyRecord.text,
                                    metaInfo = ResponseMetaInfo(historyRecord.timestamp),
                                )
                            }
                        },
                        Message.User(
                            content = message,
                            metaInfo = RequestMetaInfo.create { Clock.System.now() },
                        ),
                    ),
                    id = message.hashCode().toString(),
                ),
                model = model,
            )
        }
            .getOrElse { error ->
                val errorText = "Error while requesting LLM: ${error.message}"
                val errorTransportIds = sendMessage(errorText)
                context.messagesRepository.save(
                    MessageRecord(
                        id = MessageId.new(),
                        userId = context.chatId,
                        role = MessageRole.Assistant,
                        transportIds = errorTransportIds,
                        text = errorText,
                        timestamp = Clock.System.now(),
                        parentMessageId = userMsgId,
                        summary = null,
                    )
                )
                return
            }
            .parts
            .filterIsInstance<MessagePart.Text>()
            .joinToString(
                separator = "",
                transform = MessagePart.Text::text,
            )

        val transportIds = sendMessage(response)
        context.messagesRepository.save(
            MessageRecord(
                id = MessageId.new(),
                userId = context.chatId,
                role = MessageRole.Assistant,
                transportIds = transportIds,
                text = response,
                timestamp = Clock.System.now(),
                parentMessageId = userMsgId,
                summary = null,
            )
        )
    }
}