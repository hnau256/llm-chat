package org.hnau.llmchat.app.chat.telegram

import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import org.hnau.llmchat.app.chat.ChatServerLauncher
import org.hnau.llmchat.app.chat.dto.ChatRequest
import org.hnau.llmchat.app.chat.dto.ChatResponse
import org.hnau.llmchat.app.chat.telegram.dto.TelegramBotToken
import org.hnau.llmchat.app.chat.telegram.utils.config
import org.hnau.llmchat.app.chat.telegram.utils.telegramBot

fun ChatServerLauncher.Companion.telegramLongPolling(
    token: TelegramBotToken,
): ChatServerLauncher = ChatServerLauncher
    .create { callback: suspend (ChatRequest) -> ChatResponse ->
        telegramBot(
            token = token,
        )
            .buildBehaviourWithLongPolling {
                config(
                    callback = callback,
                )
            }
            .join()
    }