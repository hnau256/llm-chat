package org.hnau.llmchat.app.chat.telegram.utils

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.telegramBot
import org.hnau.llmchat.app.chat.telegram.dto.TelegramBotToken

internal fun telegramBot(
    token: TelegramBotToken,
): TelegramBot = telegramBot(
    token = token.token,
)