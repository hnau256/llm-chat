package org.hnau.llmchat.app.utils

import arrow.core.Either
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import org.hnau.commons.kotlin.ifNull
import org.hnau.commons.kotlin.toEither

fun Url.Companion.tryParse(
    raw: String,
    defaultProtocol: String,
): Result<Url> = runCatching {
    URLBuilder(raw)
        .takeIf { builder -> builder.protocolOrNull != null }
        .ifNull { URLBuilder("$defaultProtocol://$raw") }
        .build()
}

private val urlParser: (String) -> Either<String, Url> = { raw ->
    Url
        .tryParse(
            raw = raw,
            defaultProtocol = "https",
        )
        .toEither()
        .mapLeft { "Unable parse $raw to Url. Error: ${it.message}" }
}

val Url.Companion.parser: (String) -> Either<String, Url>
    get() = urlParser