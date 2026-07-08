package org.hnau.llmchat.app.hnauchat.page

import org.hnau.llmchat.app.chat.ChatPage
import org.hnau.llmchat.app.hnauchat.HnauChatProcessor
import org.hnau.llmchat.app.llm.model.LLMClientConfig
import org.hnau.llmchat.app.llm.model.foldRaw

suspend fun generateLLMProviderConfigButtons(
    config: LLMClientConfig,
    context: HnauChatProcessor.Context,
): List<ChatPage.Button<HnauChatProcessor.Context>> = config.foldRaw(
    ifDeepSeek = { config ->
        generateDeepSeekLLMProviderConfigButtons(
            config = config,
            context = context,
        )
    }
)