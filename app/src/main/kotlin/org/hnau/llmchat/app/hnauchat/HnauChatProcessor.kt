package org.hnau.llmchat.app.hnauchat

import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import org.hnau.commons.gen.pipe.annotations.Pipe
import org.hnau.commons.kotlin.foldNullable
import org.hnau.llmchat.app.db.DBAccessor
import org.hnau.llmchat.app.hnauchat.llmconnection.LLMConnectionManager
import org.hnau.llmchat.app.hnauchat.messages.MessageRecord
import org.hnau.llmchat.app.hnauchat.messages.MessageRole
import org.hnau.llmchat.app.hnauchat.messages.MessagesRepository
import org.hnau.llmchat.app.hnauchat.messages.StorageMessageId
import org.hnau.llmchat.app.hnauchat.messages.fold
import org.hnau.llmchat.app.hnauchat.page.generateSettingsPage
import org.hnau.llmchat.app.hnauchat.settings.UserSettingsRepository
import org.hnau.llmchat.app.hnauchat.utils.ModelsProvider
import org.hnau.llmchat.chat.api.Chat
import org.hnau.llmchat.chat.api.ChatId
import org.hnau.llmchat.chat.api.ChatMessageId
import org.hnau.llmchat.chat.api.ChatPage
import org.hnau.llmchat.chat.api.ChatProcessor
import org.hnau.llmchat.chat.api.ChatRootPage
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
            messagesRepository = MessagesRepository(
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

    private suspend fun sendMessage(
        context: Context,
        chat: Chat,
        role: MessageRole,
        text: String,
        parentMessageId: StorageMessageId,
    ) {
        val transportIds = chat.sendMessage(
            markdownText = text,
        )
        context
            .messagesRepository
            .save(
                id = StorageMessageId.new(),
                record = MessageRecord(
                    userId = context.chatId,
                    role = role,
                    transportIds = transportIds,
                    text = text,
                    timestamp = Clock.System.now(),
                    parentMessageId = parentMessageId,
                    summary = null,
                )
            )
    }

    override suspend fun handleMessage(
        context: Context,
        chat: Chat,
        transportPrompt: String,
        replayFor: ChatMessageId?,
        incomingMessageId: ChatMessageId,
        message: String
    ) {

        val userMsgId = StorageMessageId.new()

        val parentMessageId = replayFor.foldNullable(
            ifNull = {
                context.messagesRepository.findLastMessageId(
                    userId = context.chatId,
                )
            },
            ifNotNull = { replayFor ->
                context
                    .messagesRepository
                    .findByTransportId(
                        userId = context.chatId,
                        transportId = replayFor,
                    )
                    ?: run {
                        sendMessage(
                            context = context,
                            chat = chat,
                            role = MessageRole.System,
                            text = "Unable to find the message you replied to",
                            parentMessageId = userMsgId,
                        )
                        return
                    }
            }
        )

        context.messagesRepository.save(
            id = userMsgId,
            record = MessageRecord(
                userId = context.chatId,
                role = MessageRole.User,
                transportIds = listOf(incomingMessageId),
                text = message,
                timestamp = Clock.System.now(),
                parentMessageId = parentMessageId,
                summary = null,
            )
        )

        val historyMessages = parentMessageId?.let { id ->
            context.messagesRepository.findByStorageId(id)
        }

        val (client, model) = context
            .llmConnectionManager
            .client
            ?.getClientWithModel()
            ?: run {
                sendMessage(
                    context = context,
                    chat = chat,
                    role = MessageRole.System,
                    text = "Configure LLM connection before sending messages",
                    parentMessageId = userMsgId,
                )
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
                                    ifSystem = {
                                        Message.System(
                                            content = historyRecord.text,
                                            metaInfo = RequestMetaInfo(historyRecord.timestamp),
                                        )
                                    },
                                )
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
                sendMessage(
                    context = context,
                    chat = chat,
                    role = MessageRole.System,
                    text = "Error while requesting LLM: ${error.message}",
                    parentMessageId = userMsgId,
                )
                return
            }
            .parts
            .filterIsInstance<MessagePart.Text>()
            .joinToString(
                separator = "",
                transform = MessagePart.Text::text,
            )

        sendMessage(
            context = context,
            chat = chat,
            role = MessageRole.Assistant,
            text = response,
            parentMessageId = userMsgId,
        )
    }
}