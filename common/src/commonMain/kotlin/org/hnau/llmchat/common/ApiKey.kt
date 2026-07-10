package org.hnau.llmchat.common

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

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