package org.hnau.llmchat.app.utils

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.hnau.commons.kotlin.foldNullable
import org.hnau.llmchat.app.chat.dto.Port

private val portParser: (String) -> Either<String, Port> = { raw ->
    raw.toIntOrNull().foldNullable(
        ifNull = { "Unable parse '$raw' to Port: is not a number".left() },
        ifNotNull = { int ->
            Port.createOrNull(
                raw = int,
            )
                .foldNullable(
                    ifNull = { "Unable parse '$raw' to Port".left() },
                    ifNotNull = Port::right
                )
        }
    )
}

val Port.Companion.parser: (String) -> Either<String, Port>
    get() = portParser