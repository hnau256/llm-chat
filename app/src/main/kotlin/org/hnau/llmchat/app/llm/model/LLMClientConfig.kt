package org.hnau.llmchat.app.llm.model

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.OllamaModelCard
import ai.koog.prompt.executor.ollama.client.toLLModel
import ai.koog.prompt.llm.LLModel
import arrow.optics.optics
import io.ktor.http.DEFAULT_PORT
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hnau.commons.gen.fold.annotations.Fold
import org.hnau.llmchat.common.ApiKey
import org.hnau.llmchat.app.llm.utils.LLMClientDelegate

@Serializable
@Fold
sealed interface LLMClientConfig {

    val type: LLMProviderType

    fun tryCreateLLMClient(): LLMClient?

    @optics
    @Serializable
    @SerialName("deepseek")
    data class DeepSeek(
        val apiKey: ApiKey? = null,
    ) : LLMClientConfig {

        override val type: LLMProviderType
            get() = LLMProviderType.DeepSeek

        override fun tryCreateLLMClient(): LLMClient? = DeepSeekLLMClient(
            apiKey = apiKey?.value ?: return null,
        )

        companion object
    }

    @optics
    @Serializable
    @SerialName("ollama")
    data class Ollama(
        val url: Url? = null,
    ) : LLMClientConfig {

        override val type: LLMProviderType
            get() = LLMProviderType.Ollama

        override fun tryCreateLLMClient(): LLMClient? = OllamaClient(
            baseUrl = url
                ?.run {
                    URLBuilder()
                        .takeFrom(this)
                        .apply {
                            port = specifiedPort
                                .takeIf { it != DEFAULT_PORT }
                                ?: defaultPort
                        }
                        .build()
                }
                ?.toString()
                ?: return null,
        ).let { ollamaClient ->

            object : LLMClientDelegate(ollamaClient) {

                override suspend fun models(): List<LLModel> = ollamaClient
                    .getModels()
                    .map(OllamaModelCard::toLLModel)
            }
        }

        companion object {

            private const val defaultPort: Int = 11434
        }
    }
}