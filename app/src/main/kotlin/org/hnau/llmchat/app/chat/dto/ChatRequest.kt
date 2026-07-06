package org.hnau.llmchat.app.chat.dto

data class ChatRequest(
    val userId: UserId,
    val message: String,
)