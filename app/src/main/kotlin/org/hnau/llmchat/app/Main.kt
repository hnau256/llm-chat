package org.hnau.llmchat.app

import arrow.core.getOrElse
import arrow.core.right
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import io.ktor.http.Url
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngineFactory
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.hnau.commons.kotlin.foldNullable
import org.hnau.llmchat.app.db.DBAccessor
import org.hnau.llmchat.app.db.DBAdapter
import org.hnau.llmchat.app.db.sqlite
import org.hnau.llmchat.app.dto.Port
import org.hnau.llmchat.app.hnauchat.HnauChatProcessor
import org.hnau.llmchat.app.telegram.TelegramWebhookConfig
import org.hnau.llmchat.app.telegram.launchTelegramChat
import org.hnau.llmchat.app.utils.fileParser
import org.hnau.llmchat.app.utils.getEnv
import org.hnau.llmchat.app.utils.getRequiredEnv
import org.hnau.llmchat.app.utils.parser

private val logger = Logger.withTag("Main")

fun main() {

    Logger.setLogWriters(platformLogWriter())
    Logger.setMinSeverity(Severity.Debug)

    val healthPort: Port? = getEnv(
        name = "HEALTH_PORT",
        parser = Port.parser,
    ).getOrNull()

    val telegramToken: String = getRequiredEnv(
        name = "TELEGRAM_BOT_TOKEN",
        parser = { it.right() },
    )

    val telegramWebhookUrl: Url? = getEnv(
        name = "TELEGRAM_WEBHOOK_URL",
        parser = Url.parser,
    ).getOrNull()

    val telegramWebhookPort: Port = getEnv(
        name = "TELEGRAM_WEBHOOK_PORT",
        parser = Port.parser,
    ).getOrElse { Port.createOrNull(8080)!! }

    val databaseFile = getRequiredEnv(
        name = "DB_PATH",
        parser = fileParser,
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

        launchTelegramChat(
            token = telegramToken,
            webhook = telegramWebhookUrl?.let { url ->
                TelegramWebhookConfig(
                    url = url,
                    port = telegramWebhookPort,
                    serverFactory = serverFactory,
                )
            },
            chatProcessor = HnauChatProcessor(
                db = DBAccessor.create(
                    adapter = DBAdapter.sqlite(
                        databaseFile = databaseFile
                    )
                )
            )
        )
    }
}

private val serverFactory: ApplicationEngineFactory<*, *> = CIO
