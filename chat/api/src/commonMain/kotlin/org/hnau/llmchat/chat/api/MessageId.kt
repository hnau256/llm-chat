package org.hnau.llmchat.chat.api

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class MessageId(
    val id: String,
) {

    companion object {

        fun new(): MessageId = MessageId(
            id = Uuid.random().toString(),
        )
    }
}