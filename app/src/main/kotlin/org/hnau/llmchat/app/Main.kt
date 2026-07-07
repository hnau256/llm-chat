package org.hnau.llmchat.app

import arrow.core.getOrElse
import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter
import io.ktor.http.Url
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngineFactory
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.hnau.commons.kotlin.foldNullable
import org.hnau.llmchat.app.chat.ChatServerLauncher
import org.hnau.llmchat.app.chat.dto.ChatResponse
import org.hnau.llmchat.app.chat.dto.Port
import org.hnau.llmchat.app.chat.telegram.dto.TelegramBotToken
import org.hnau.llmchat.app.chat.telegram.telegramLongPolling
import org.hnau.llmchat.app.chat.telegram.telegramWebhook
import org.hnau.llmchat.app.db.DBAccessor
import org.hnau.llmchat.app.db.sqlite
import org.hnau.llmchat.app.llm.LLMChat
import org.hnau.llmchat.app.utils.fileParser
import org.hnau.llmchat.app.utils.getEnv
import org.hnau.llmchat.app.utils.getRequiredEnv
import org.hnau.llmchat.app.utils.parser

private val logger = Logger.withTag("Main")

fun main() {

    Logger.setLogWriters(platformLogWriter())

    val healthPort: Port? = getEnv(
        name = "HEALTH_PORT",
        parser = Port.parser,
    ).getOrNull()

    val telegramToken: TelegramBotToken = getRequiredEnv(
        name = "TELEGRAM_BOT_TOKEN",
        parser = TelegramBotToken.parser,
    )

    val telegramWebhookUrl: Url? = getEnv(
        name = "TELEGRAM_WEBHOOK_URL",
        parser = Url.parser,
    ).getOrNull()

    val telegramWebhookPort: Port = getEnv(
        name = "TELEGRAM_WEBHOOK_PORT",
        parser = Port.parser,
    ).getOrElse { Port.createOrNull(8080)!! }

    val dbAccessor: DBAccessor = DBAccessor.sqlite(
        databaseFile = getRequiredEnv(
            name = "DB_PATH",
            parser = fileParser,
        )
    )

    runBlocking {

        healthPort.foldNullable(
            ifNull = { logger.d { "No need to launch health server" } },
            ifNotNull = { positiveHealthPort ->
                logger.i { "Launching health server on port $positiveHealthPort" }
                launch {
                    httpHealthServer(
                        factory = serverFactory,
                        port = positiveHealthPort,
                    )
                }
            }
        )

        val chatServerLauncher: ChatServerLauncher = telegramWebhookUrl.foldNullable(
            ifNull = {
                logger.d { "Launching telegram bot in long polling mode" }
                ChatServerLauncher.telegramLongPolling(
                    token = telegramToken,
                )
            },
            ifNotNull = { url ->
                logger.d { "Launching telegram bot in webhook mode. Url:$url, port:${telegramWebhookPort.value}" }
                ChatServerLauncher.telegramWebhook(
                    token = telegramToken,
                    port = telegramWebhookPort,
                    factory = serverFactory,
                    webhookUrl = url,
                )
            }
        )

        val chat = LLMChat(
            dbAccessor = dbAccessor,
        )

        chatServerLauncher.launchChatServer(
            callback = chat::handleRequest,
        )
    }
}

private val serverFactory: ApplicationEngineFactory<*, *> = CIO
