package org.hnau.llmchat.app.dto

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class ApiKey private constructor(
    val value: String,
) {

    companion object {

        fun tryCreate(
            raw: String,
        ): ApiKey? = raw
            .takeIf(String::isNotEmpty)
            ?.let(::ApiKey)
    }
}