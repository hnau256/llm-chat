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
    data class DeepSeek @JvmOverloads constructor(
        val apiKey: ApiKey? = null,
    ) : LLMProviderConfig {

        override fun tryCreateConfig(): LLMProviderConfig? = LLMProviderConfig.DeepSeek(
            apiKey = apiKey ?: return null,
        )
    }

    companion object {

        val all: List<LLMProviderConfig> by lazy {
            LLMProviderConfig::class.sealedSubclasses.map { subclass ->
                subclass.java.getDeclaredConstructor().newInstance() as LLMProviderConfig
            }
        }
    }
}