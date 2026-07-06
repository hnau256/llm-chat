package org.hnau.llmchat.app.chat.dto

@JvmInline
value class Port private constructor(
    val value: Int,
) {

    companion object {

        fun createOrNull(
            raw: Int,
        ): Port? = raw
            .takeIf { it > 0 }
            ?.let(::Port)
    }
}