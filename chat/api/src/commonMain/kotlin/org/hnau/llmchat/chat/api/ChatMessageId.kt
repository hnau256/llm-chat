package org.hnau.llmchat.chat.api

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class ChatMessageId(
    val id: String,
)