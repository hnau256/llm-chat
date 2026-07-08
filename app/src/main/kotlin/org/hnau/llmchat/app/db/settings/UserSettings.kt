package org.hnau.llmchat.app.db.settings

import kotlinx.serialization.Serializable
import org.hnau.llmchat.app.llm.model.LLMClientConfig

@Serializable
data class UserSettings(
    val basePrompt: String = "",
    val llmClientConfig: LLMClientConfig? = null,
)
