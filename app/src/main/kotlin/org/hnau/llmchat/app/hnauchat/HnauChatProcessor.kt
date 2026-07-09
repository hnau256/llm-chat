package org.hnau.llmchat.app.hnauchat

import org.hnau.llmchat.app.chat.Chat
import org.hnau.llmchat.app.chat.ChatId
import org.hnau.llmchat.app.chat.ChatPage
import org.hnau.llmchat.app.chat.ChatProcessor
import org.hnau.llmchat.app.chat.ChatRootPage
import org.hnau.llmchat.app.db.DBAccessor
import org.hnau.llmchat.app.hnauchat.llmconnection.LLMConnectionManager
import org.hnau.llmchat.app.hnauchat.settings.UserSettingsRepository
import org.hnau.llmchat.app.hnauchat.page.generateSettingsPage
import org.hnau.llmchat.app.hnauchat.utils.ModelsProvider

class HnauChatProcessor(
    private val db: DBAccessor,
    private val modelsProvider: ModelsProvider,
) : ChatProcessor<HnauChatProcessor.Context> {

    data class Context(
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
            db = db,
            chatId = chatId,
        )

        return Context(
            settings = settings,
            llmConnectionManager = LLMConnectionManager(
                settings = settings,
                modelsProvider = modelsProvider,
            )
        )
    }

    override suspend fun Chat.handleMessage(
        context: Context,
        message: String
    ) {
        sendMessage("Answer for $message")
    }
}