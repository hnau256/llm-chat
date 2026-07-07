package org.hnau.llmchat.app.llm.model

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient

fun LLMProviderConfig.tryCreateLLMProvider(): LLMClient? = fold<LLMClient>(
    ifDeepSeek = { apiKey ->
        DeepSeekLLMClient(
            apiKey = apiKey?.value ?: return null,
        )
    },
)