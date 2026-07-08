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

    fun tryCreateLLMClient(): LLMClient?

    @Serializable
    @SerialName("deepseek")
    data class DeepSeek @JvmOverloads constructor(
        val apiKey: ApiKey? = null,
    ) : LLMClientConfig {

        override fun tryCreateLLMClient(): LLMClient? = DeepSeekLLMClient(
            apiKey = apiKey?.value ?: return null,
        )
    }

    companion object {

        val all: List<LLMClientConfig> by lazy {
            LLMClientConfig::class.sealedSubclasses.map { subclass ->
                subclass.java.getDeclaredConstructor().newInstance() as LLMClientConfig
            }
        }
    }
}