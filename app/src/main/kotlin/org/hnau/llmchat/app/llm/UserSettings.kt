package org.hnau.llmchat.app.llm

import kotlinx.serialization.Serializable

@Serializable
data class UserSettings(
    val basePrompt: String = "",
)
