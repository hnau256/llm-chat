package org.hnau.llmchat.app

import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.awaitCancellation
import org.hnau.llmchat.common.Port

suspend fun httpHealthServer(
    port: Port,
    factory: ApplicationEngineFactory<*, *> = CIO,
) {
    val server = embeddedServer(
        factory = factory,
        port = port.value,
    ) {
        routing {
            get("/health") {
                call.respondText("OK")
            }
        }
    }

    try {
        server.start()
        awaitCancellation()
    } finally {
        server.stop()
    }
}
