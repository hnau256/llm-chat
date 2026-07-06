package org.hnau.llmchat.app

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.awaitCancellation

suspend fun httpHealthServer(
    port: Int = 8080,
) {
    val server = embeddedServer(
        factory = CIO,
        port = port,
    ) {
        routing {
            get("/health") {
                call.respondText("OK")
            }
        }
    }

    try {
        server.start(wait = true)
        awaitCancellation()
    } finally {
        server.stop()
    }
}
