package org.hnau.llmchat.app.hnauchat.page

import org.hnau.llmchat.app.chat.ChatPage
import org.hnau.llmchat.app.hnauchat.HnauChatProcessor
import org.hnau.llmchat.app.llm.model.LLMProviderConfig

suspend fun generateLLMProviderConfigButtons(
    config: LLMProviderConfig,
    context: HnauChatProcessor.Context,
): List<ChatPage.Button<HnauChatProcessor.Context>> = when (config) {
    is LLMProviderConfig.DeepSeek -> generateDeepSeekLLMProviderConfigButtons(
        config = config,
        context = context,
    )
}