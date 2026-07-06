package org.hnau.llmchat.app.chat.telegram.dto

@JvmInline
value class TelegramBotToken private constructor(
    val token: String,
) {

    companion object {

        fun createOrNull(
            raw: String,
        ): TelegramBotToken? = raw
            .takeIf(String::isNotEmpty)
            ?.let(::TelegramBotToken)
    }
}