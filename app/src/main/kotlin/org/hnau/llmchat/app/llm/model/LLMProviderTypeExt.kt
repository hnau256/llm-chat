package org.hnau.llmchat.app.llm.model

fun LLMProviderType.createBaseConfig(): LLMClientConfig = fold(
    ifDeepSeek = { LLMClientConfig.DeepSeek() }
)