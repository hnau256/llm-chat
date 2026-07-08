package org.hnau.llmchat.app.hnauchat

import org.hnau.llmchat.app.chat.Chat
import org.hnau.llmchat.app.chat.ChatId
import org.hnau.llmchat.app.chat.ChatProcessor
import org.hnau.llmchat.app.chat.ChatRootPage
import org.hnau.llmchat.app.db.DBAccessor
import org.hnau.llmchat.app.db.settings.UserSettingsRepository
import org.hnau.llmchat.app.hnauchat.page.generateSettingsPage

class HnauChatProcessor(
    private val db: DBAccessor,
) : ChatProcessor<HnauChatProcessor.Context> {

    data class Context(
        val settings: UserSettingsRepository,
    )

    override val rootPages: List<ChatRootPage<Context>> = listOf(
        ChatRootPage(
            title = "Settings",
            generatePage = { context ->
                generateSettingsPage(context)
            }
        )
    )

    override suspend fun buildContext(
        chatId: ChatId,
    ): Context = Context(
        settings = UserSettingsRepository.create(
            db = db,
            chatId = chatId,
        )
    )

    override suspend fun Chat.handleMessage(
        context: Context,
        message: String
    ) {
        sendMessage("Answer for $message")
    }
}