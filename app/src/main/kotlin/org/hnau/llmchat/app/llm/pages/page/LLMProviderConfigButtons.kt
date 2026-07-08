package org.hnau.llmchat.app.llm.pages.page

import org.hnau.llmchat.app.llm.LLMChatContext
import org.hnau.llmchat.app.llm.model.LLMProviderConfig
import org.hnau.llmchat.app.telegram.TelegramPageMessage

suspend fun LLMChatContext.generateLLMProviderConfigButtons(
    config: LLMProviderConfig,
): List<TelegramPageMessage.Button> = when (config) {
    is LLMProviderConfig.DeepSeek -> generateDeepSeekLLMProviderConfigButtons(
        config = config,
    )
}