package org.hnau.llmchat.app.telegram

import io.ktor.http.Url
import io.ktor.server.engine.ApplicationEngineFactory
import org.hnau.llmchat.app.dto.Port

internal data class TelegramWebhookConfig(
    val url: Url,
    val port: Port,
    val serverFactory: ApplicationEngineFactory<*, *>,
)