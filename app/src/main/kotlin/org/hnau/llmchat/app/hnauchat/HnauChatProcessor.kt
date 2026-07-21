package org.hnau.llmchat.app.hnauchat

import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import org.hnau.commons.gen.pipe.annotations.Pipe
import org.hnau.llmchat.chat.api.Chat
import org.hnau.llmchat.chat.api.ChatId
import org.hnau.llmchat.chat.api.ChatPage
import org.hnau.llmchat.chat.api.ChatProcessor
import org.hnau.llmchat.chat.api.ChatRootPage
import org.hnau.llmchat.chat.api.MessageId
import org.hnau.llmchat.app.db.DBAccessor
import org.hnau.llmchat.app.hnauchat.llmconnection.LLMConnectionManager
import org.hnau.llmchat.app.hnauchat.messages.MessageRecord
import org.hnau.llmchat.app.hnauchat.messages.MessagesRepository
import org.hnau.llmchat.app.hnauchat.page.generateSettingsPage
import org.hnau.llmchat.app.hnauchat.settings.UserSettingsRepository
import org.hnau.llmchat.app.hnauchat.utils.ModelsProvider
import java.util.UUID
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
        val messagesRepo: MessagesRepository,
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
            messagesRepo = MessagesRepository.create(
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

        val parentDbId = replayFor?.let { replayId ->
            context.messagesRepo.findByTransportId(
                userId = context.chatId.id,
                transportId = replayId.id,
            )
        } ?: context.messagesRepo.findLastMessageId(
            userId = context.chatId.id,
        )

        val userMsgId = UUID.randomUUID().toString()
        val now = Clock.System.now().toEpochMilliseconds()

        context.messagesRepo.save(
            MessageRecord(
                id = userMsgId,
                userId = context.chatId.id,
                transportIds = listOf(incomingMessageId.id),
                text = message,
                timestamp = now,
                parentMessageId = parentDbId,
                summary = null,
            )
        )

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
                sendMessage("Error while requesting LLM: ${error.message}")
                return
            }
            .parts
            .filterIsInstance<MessagePart.Text>()
            .joinToString(
                separator = "",
                transform = MessagePart.Text::text,
            )

        val transportIds = sendMessage(response)
        context.messagesRepo.save(
            MessageRecord(
                id = UUID.randomUUID().toString(),
                userId = context.chatId.id,
                transportIds = transportIds.map { it.id },
                text = response,
                timestamp = Clock.System.now().toEpochMilliseconds(),
                parentMessageId = userMsgId,
                summary = null,
            )
        )
    }
}