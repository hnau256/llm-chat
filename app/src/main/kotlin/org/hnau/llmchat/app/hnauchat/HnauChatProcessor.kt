package org.hnau.llmchat.app.hnauchat

import ai.koog.prompt.Prompt
import ai.koog.prompt.message.MessagePart
import org.hnau.commons.gen.pipe.annotations.Pipe
import org.hnau.commons.kotlin.foldNullable
import org.hnau.llmchat.app.db.DBAccessor
import org.hnau.llmchat.app.hnauchat.llmconnection.LLMConnectionManager
import org.hnau.llmchat.app.hnauchat.messages.MessageRecord
import org.hnau.llmchat.app.hnauchat.messages.MessageRole
import org.hnau.llmchat.app.hnauchat.messages.MessagesRepository
import org.hnau.llmchat.app.hnauchat.messages.StorageMessageId
import org.hnau.llmchat.app.hnauchat.messages.buildLLMChatMessages
import org.hnau.llmchat.app.hnauchat.page.generateSettingsPage
import org.hnau.llmchat.app.hnauchat.settings.ChatSettingsRepository
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
            settings: ChatSettingsRepository,
        ): LLMConnectionManager.Dependencies

        companion object
    }

    data class Context(
        val messagesRepository: MessagesRepository,
        val settings: ChatSettingsRepository,
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

        val settings = ChatSettingsRepository.create(
            db = dependencies.db,
            chatId = chatId,
        )

        return Context(
            messagesRepository = MessagesRepository(
                db = dependencies.db,
                chatId = chatId,
            ),
            settings = settings,
            llmConnectionManager = LLMConnectionManager(
                dependencies = dependencies.llmConnectionManager(
                    settings = settings,
                )
            )
        )
    }

    private suspend fun sendAndSaveMessage(
        messagesRepository: MessagesRepository,
        chat: Chat,
        role: MessageRole,
        text: String,
        parentMessageId: StorageMessageId,
    ) {
        val transportIds = chat.sendMessage(
            markdownText = text,
        )
        messagesRepository.save(
            id = StorageMessageId.new(),
            record = MessageRecord(
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

        val chatMsgId = StorageMessageId.new()

        val parentMessageId = replayFor.foldNullable(
            ifNull = { context.messagesRepository.findLastMessageId() },
            ifNotNull = { replayFor ->
                context.messagesRepository
                    .findByTransportId(
                        transportId = replayFor,
                    )
                    ?: run {
                        chat.sendMessage(
                            markdownText = "Unable to find the message you replied to",
                        )
                        return
                    }
            }
        )

        context.messagesRepository.save(
            id = chatMsgId,
            record = MessageRecord(
                role = MessageRole.User,
                transportIds = listOf(incomingMessageId),
                text = message,
                timestamp = Clock.System.now(),
                parentMessageId = parentMessageId,
                summary = null,
            )
        )

        val (client, model) = context
            .llmConnectionManager
            .client
            ?.getClientWithModel()
            ?: run {
                chat.sendMessage(
                    markdownText = "Configure LLM connection before sending messages",
                )
                return
            }

        val response = runCatching {
            client.execute(
                prompt = Prompt(
                    messages = buildLLMChatMessages(
                        transportPrompt = transportPrompt,
                        context = context,
                        userMessage = message,
                        parentMessageId = parentMessageId,
                    ),
                    id = message.hashCode().toString(),
                ),
                model = model,
            )
        }
            .getOrElse { error ->
                chat.sendMessage(
                    markdownText = "Error while requesting LLM: ${error.message}",
                )
                return
            }
            .parts
            .filterIsInstance<MessagePart.Text>()
            .joinToString(
                separator = "",
                transform = MessagePart.Text::text,
            )

        sendAndSaveMessage(
            messagesRepository = context.messagesRepository,
            chat = chat,
            role = MessageRole.Assistant,
            text = response,
            parentMessageId = chatMsgId,
        )
    }
}