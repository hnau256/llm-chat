package org.hnau.llmchat.app.telegram

import co.touchlab.kermit.Logger
import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.extensions.api.webhook.setWebhookInfo
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContextReceiver
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviour
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.utils.updates.retrieving.includeWebhookHandlingInRoute
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import kotlinx.coroutines.awaitCancellation
import org.hnau.commons.kotlin.foldNullable

private val logger = Logger.withTag("LaunchTelegramBot")

internal suspend fun launchTelegramBot(
    token: String,
    webhook: TelegramWebhookConfig?,
    config: BehaviourContextReceiver<Unit>,
) {
    val bot = telegramBot(token)
    webhook.foldNullable(
        ifNull = {
            logger.i { "Launching telegram bot in long polling mode" }
            bot
                .buildBehaviourWithLongPolling(block = config)
                .join()
        },
        ifNotNull = { webhookConfig ->
            logger.i { "Launching telegram bot in webhook mode. Url:${webhookConfig.url}, Port:${webhookConfig.port.value}" }

            val behaviourContext = bot.buildBehaviour(block = config)

            bot.setWebhookInfo(webhookConfig.url.toString())

            val server = embeddedServer(
                factory = webhookConfig.serverFactory,
                port = webhookConfig.port.value,
            ) {
                routing {
                    includeWebhookHandlingInRoute(
                        scope = behaviourContext,
                        block = behaviourContext.asUpdateReceiver,
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
    )
}