package org.hnau.llmchat.app.utils

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.hnau.commons.kotlin.foldNullable
import org.hnau.llmchat.app.chat.telegram.dto.TelegramBotToken

private val telegramBotTokenParser: (String) -> Either<String, TelegramBotToken> = { raw ->
    TelegramBotToken
        .createOrNull(
            raw = raw,
        )
        .foldNullable(
            ifNull = { "Unable parse '$raw' to TelegramBotToken".left() },
            ifNotNull = TelegramBotToken::right
        )
}

val TelegramBotToken.Companion.parser: (String) -> Either<String, TelegramBotToken>
    get() = telegramBotTokenParser