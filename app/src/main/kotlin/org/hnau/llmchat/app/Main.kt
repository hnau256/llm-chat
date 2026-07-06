package org.hnau.llmchat.app

import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.hnau.commons.kotlin.foldNullable


private val logger = Logger.withTag("Main")

fun main(
    args: Array<String>,
) {

    Logger.setLogWriters(platformLogWriter())

    val parser = ArgParser("LLMChat")

    val healthPort by parser
        .option(
            type = ArgType.Int,
            fullName = "health-port",
            shortName = "p",
            description = "Health check port"
        )
        .default(0)

    parser.parse(args)

    runBlocking {

        healthPort
            .takeIf { it > 0 }
            .foldNullable(
                ifNull = { logger.d { "No need to launch health server" } },
                ifNotNull = { positiveHealthPort ->
                    logger.i { "Launching health server on port $positiveHealthPort" }
                    launch {
                        httpHealthServer(
                            port = positiveHealthPort,
                        )
                    }
                }
            )
    }

}