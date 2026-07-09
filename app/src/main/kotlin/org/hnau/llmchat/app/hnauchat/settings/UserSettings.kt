package org.hnau.llmchat.app.hnauchat.settings

import kotlinx.serialization.Serializable
import org.hnau.commons.kotlin.KeyValue
import org.hnau.llmchat.app.llm.model.LLMClientConfig
import org.hnau.llmchat.app.llm.model.LLMProviderType

@Serializable
data class UserSettings(
    val basePrompt: String = "",
    val llmClientConfig: LLMClientConfig? = null,
    val model: KeyValue<LLMProviderType, String>? = null,
)
