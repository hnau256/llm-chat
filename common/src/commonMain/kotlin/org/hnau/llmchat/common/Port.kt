package org.hnau.llmchat.common

import kotlin.jvm.JvmInline

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
