package org.hnau.llmchat.app.utils

import kotlinx.cli.ArgType
import org.hnau.llmchat.app.chat.telegram.dto.TelegramBotToken

private object TelegramBotTokenArgType : ArgType<TelegramBotToken>(
    hasParameter = true,
) {
    override fun convert(
        value: kotlin.String,
        name: kotlin.String
    ): TelegramBotToken = TelegramBotToken
        .createOrNull(
            raw = value,
        )
        ?: error("Incorrect telegram bot token '$value'")

    override val description: kotlin.String = "Telegram bot token"
}

internal val ArgType.Companion.telegramBotToken: ArgType<TelegramBotToken>
    get() = TelegramBotTokenArgType