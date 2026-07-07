package org.hnau.llmchat.app.llm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hnau.commons.gen.fold.annotations.Fold
import org.hnau.llmchat.app.dto.ApiKey

@Serializable
@Fold
sealed interface LLMProviderConfig {

    fun tryCreateConfig(): LLMProviderConfig?

    @Serializable
    @SerialName("deepseek")
    data class DeepSeek(
        val apiKey: ApiKey? = null,
    ) : LLMProviderConfig {

        override fun tryCreateConfig(): LLMProviderConfig? = LLMProviderConfig.DeepSeek(
            apiKey = apiKey ?: return null,
        )
    }
}