package org.hnau.llmchat.app.chat.telegram

import org.hnau.llmchat.app.chat.telegram.utils.telegramBot
import dev.inmo.tgbotapi.extensions.api.webhook.setWebhookInfo
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviour
import dev.inmo.tgbotapi.extensions.utils.updates.retrieving.includeWebhookHandlingInRoute
import io.ktor.http.Url
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import kotlinx.coroutines.awaitCancellation
import org.hnau.llmchat.app.chat.ChatServerLauncher
import org.hnau.llmchat.app.chat.dto.ChatRequest
import org.hnau.llmchat.app.chat.dto.ChatResponse
import org.hnau.llmchat.app.chat.dto.Port
import org.hnau.llmchat.app.chat.telegram.dto.TelegramBotToken
import org.hnau.llmchat.app.chat.telegram.utils.config

fun ChatServerLauncher.Companion.telegramWebhook(
    token: TelegramBotToken,
    port: Port,
    factory: ApplicationEngineFactory<*, *> = CIO,
    webhookUrl: Url,
): ChatServerLauncher = ChatServerLauncher
    .create { callback: suspend (ChatRequest) -> ChatResponse ->

        val bot = telegramBot(
            token = token,
        )

        val behaviourContext = bot.buildBehaviour {
            config(
                callback = callback,
            )
        }

        bot.setWebhookInfo(
            url = webhookUrl.toString(),
        )

        val server = embeddedServer(
            factory = factory,
            port = port.value,
        ) {
            routing {
                includeWebhookHandlingInRoute(
                    scope = behaviourContext,
                    block = behaviourContext.asUpdateReceiver
                )
            }
        }


        try {
            server.start()
            awaitCancellation()
        } finally {
            server.stop()
        }
    }