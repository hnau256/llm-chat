package org.hnau.llmchat.app.llm.model

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hnau.commons.gen.fold.annotations.Fold
import org.hnau.llmchat.app.dto.ApiKey

@Serializable
@Fold
sealed interface LLMClientConfig {

    val type: LLMProviderType

    fun tryCreateLLMClient(): LLMClient?

    @Serializable
    @SerialName("deepseek")
    data class DeepSeek @JvmOverloads constructor(
        val apiKey: ApiKey? = null,
    ) : LLMClientConfig {

        override val type: LLMProviderType
            get() = LLMProviderType.DeepSeek

        override fun tryCreateLLMClient(): LLMClient? = DeepSeekLLMClient(
            apiKey = apiKey?.value ?: return null,
        )
    }
}