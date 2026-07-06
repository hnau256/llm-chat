package org.hnau.llmchat.app.utils

import arrow.core.Either
import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.toOption

internal fun <T> getEnv(
    name: String,
    parser: (String) -> Either<String, T>,
): Option<T> = System
    .getenv(name)
    .toOption()
    .map { encoded ->
        parser(encoded).getOrElse { errorMessage ->
            error("Unable parse $name: $errorMessage")
        }
    }

internal fun <T> getRequiredEnv(
    name: String,
    parser: (String) -> Either<String, T>,
): T = getEnv(
    name = name,
    parser = parser,
).getOrElse {
    error("$name is required")
}