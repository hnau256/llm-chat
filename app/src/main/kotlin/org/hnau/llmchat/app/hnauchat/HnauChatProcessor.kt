package org.hnau.llmchat.app.hnauchat

import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
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

    companion object {
        private const val MAX_HISTORY_DEPTH = 100
        private const val HISTORY_TAIL = 10
        private const val HISTORY_LIMIT = 50
    }

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

        val userMessage = MessageRecord(
            role = MessageRole.User,
            transportIds = listOf(incomingMessageId),
            text = message,
            timestamp = Clock.System.now(),
            parentMessageId = parentMessageId,
            summary = null,
        )

        context.messagesRepository.save(
            id = chatMsgId,
            record = userMessage,
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

        val rawHistory = parentMessageId?.let { id ->
            context.messagesRepository.getHistory(id, MAX_HISTORY_DEPTH, HISTORY_TAIL)
        }.orEmpty()

        val (historyMessages, historySummary) = if (rawHistory.size <= HISTORY_LIMIT) {
            rawHistory to null
        } else {
            val tailMessages = rawHistory.takeLast(HISTORY_TAIL)
            val toSummarize = rawHistory.dropLast(HISTORY_TAIL)

            val summaryText = run {
                val prefix = buildLLMChatMessages(
                    transportPrompt = transportPrompt,
                    context = context,
                    historyMessages = toSummarize,
                    userMessage = null,
                )
                val summaryPrompt = Prompt(
                    messages = prefix + Message.User(
                        content = "Summarize the conversation above concisely, preserving all key context, decisions, and information. Return only the summary.",
                        metaInfo = RequestMetaInfo(Clock.System.now()),
                    ),
                    id = "summary-${toSummarize.hashCode()}",
                )
                client.execute(prompt = summaryPrompt, model = model)
                    .parts
                    .filterIsInstance<MessagePart.Text>()
                    .joinToString(separator = "") { it.text }
            }

            context.messagesRepository.updateSummary(
                id = tailMessages.first().parentMessageId!!,
                summary = summaryText,
            )

            tailMessages to summaryText
        }

        val response = runCatching {
            client.execute(
                prompt = Prompt(
                    messages = buildLLMChatMessages(
                        transportPrompt = transportPrompt,
                        context = context,
                        historyMessages = historyMessages,
                        userMessage = userMessage,
                        summary = historySummary,
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