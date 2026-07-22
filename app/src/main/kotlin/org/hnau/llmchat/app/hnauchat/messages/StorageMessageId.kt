package org.hnau.llmchat.app.hnauchat.messages

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class StorageMessageId(
    val id: String,
) {

    companion object {

        fun new(): StorageMessageId = StorageMessageId(
            id = Uuid.random().toString(),
        )
    }
}