package org.hnau.llmchat.app.utils

import kotlinx.cli.ArgType
import org.hnau.llmchat.app.chat.dto.Port

private object PortArgType : ArgType<Port>(
    hasParameter = true,
) {
    override fun convert(
        value: kotlin.String,
        name: kotlin.String
    ): Port = Port
        .createOrNull(
            raw = value.toInt(),
        )
        ?: error("Incorrect port '$value'")

    override val description: kotlin.String = "Port"
}

internal val ArgType.Companion.port: ArgType<Port>
    get() = PortArgType