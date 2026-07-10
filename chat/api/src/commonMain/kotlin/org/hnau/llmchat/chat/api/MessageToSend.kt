package org.hnau.llmchat.chat.api

import kotlin.jvm.JvmInline

@JvmInline
value class MessageToSend(
    val text: String,
)