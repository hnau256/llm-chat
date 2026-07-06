package org.hnau.llmchat.app

import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter
import io.ktor.http.Url
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngineFactory
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.hnau.commons.kotlin.foldNullable
import org.hnau.llmchat.app.chat.ChatServerLauncher
import org.hnau.llmchat.app.chat.dto.ChatResponse
import org.hnau.llmchat.app.chat.dto.Port
import org.hnau.llmchat.app.chat.telegram.dto.TelegramBotToken
import org.hnau.llmchat.app.chat.telegram.telegramLongPolling
import org.hnau.llmchat.app.chat.telegram.telegramWebhook
import org.hnau.llmchat.app.utils.port
import org.hnau.llmchat.app.utils.telegramBotToken
import org.hnau.llmchat.app.utils.url


private val logger = Logger.withTag("Main")

fun main(
    args: Array<String>,
) {

    Logger.setLogWriters(platformLogWriter())

    val parser = ArgParser("LLMChat")

    val healthPort: Port? by parser
        .option(
            type = ArgType.port,
            fullName = "health-port",
            description = "Health check port"
        )

    val telegramToken: TelegramBotToken by parser
        .option(
            type = ArgType.telegramBotToken,
            fullName = "telegram-bot-token",
            shortName = "t",
            description = "Telegram bot token",
        )
        .required()

    val telegramWebhookUrl: Url? by parser
        .option(
            type = ArgType.url,
            fullName = "telegram-webhook-url",
            shortName = "u",
            description = "Telegram bot webhook url",
        )

    val telegramWebhookPort: Port by parser
        .option(
            type = ArgType.port,
            fullName = "telegram-webhook-port",
            shortName = "p",
            description = "Telegram bot webhook port",
        )
        .default(
            Port.createOrNull(8080)!!,
        )

    parser.parse(args)

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

        chatServerLauncher.launchChatServer { request ->
            logger.i { "Handling message from user ${request.userId}" }
            val message = request.message
            ChatResponse(
                message = "You said: $message"
            )
        }
    }
}

private val serverFactory: ApplicationEngineFactory<*, *> = CIO