package org.hnau.llmchat.app.db.settings

import kotlinx.serialization.Serializable
import org.hnau.llmchat.app.llm.model.LLMProviderConfig

@Serializable
data class UserSettings(
    val basePrompt: String = "",
    val llmProviderConfig: LLMProviderConfig? = null,
)
