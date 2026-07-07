package org.hnau.llmchat.app.utils

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.hnau.commons.kotlin.foldNullable
import java.io.File

private val portParser: (String) -> Either<String, File> = { raw ->
    raw
        .takeIf(String::isNotEmpty)
        .foldNullable(
            ifNull = { "Unable parse $raw to File: is empty".left() },
            ifNotNull = { nonEmpty -> File(nonEmpty).right() },
        )
}

val fileParser: (String) -> Either<String, File>
    get() = portParser