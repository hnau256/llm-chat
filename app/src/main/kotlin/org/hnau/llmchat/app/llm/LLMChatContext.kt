package org.hnau.llmchat.app.llm

import org.hnau.llmchat.app.db.settings.UserSettingsRepository
import org.hnau.llmchat.app.telegram.TelegramChat

data class LLMChatContext(
    val chat: TelegramChat,
    val userSettings: UserSettingsRepository,
)