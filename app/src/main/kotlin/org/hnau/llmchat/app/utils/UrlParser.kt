package org.hnau.llmchat.app.utils

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import org.hnau.commons.kotlin.foldNullable

private val portParser: (String) -> Either<String, Url> = { raw ->
    raw
        .takeIf(String::isNotEmpty)
        .foldNullable(
            ifNull = { "Unable parse $raw to Url: is empty".left() },
            ifNotNull = { nonEmpty ->
                URLBuilder(nonEmpty)
                    .build()
                    .right()
            }
        )
}

val Url.Companion.parser: (String) -> Either<String, Url>
    get() = portParser