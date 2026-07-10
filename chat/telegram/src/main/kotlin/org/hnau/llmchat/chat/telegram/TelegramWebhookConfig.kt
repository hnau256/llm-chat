package org.hnau.llmchat.chat.telegram

import io.ktor.http.Url
import io.ktor.server.engine.ApplicationEngineFactory
import org.hnau.llmchat.common.Port

data class TelegramWebhookConfig(
    val url: Url,
    val port: Port,
    val serverFactory: ApplicationEngineFactory<*, *>,
)