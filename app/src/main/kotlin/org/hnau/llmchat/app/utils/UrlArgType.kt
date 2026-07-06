package org.hnau.llmchat.app.utils

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.cli.ArgType

private object UrlArgType : ArgType<Url>(
    hasParameter = true,
) {
    override fun convert(
        value: kotlin.String,
        name: kotlin.String
    ): Url = URLBuilder(
        urlString = value,
    ).build()

    override val description: kotlin.String = "Url"
}

internal val ArgType.Companion.url: ArgType<Url>
    get() = UrlArgType