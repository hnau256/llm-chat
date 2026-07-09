package org.hnau.llmchat.app.llm.utils

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message

open class LLMClientDelegate(
    private val delegate: LLMClient,
) : LLMClient() {

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Message.Assistant = delegate.execute(
        prompt = prompt,
        model = model,
        tools = tools,
    )

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult = delegate.moderate(
        prompt = prompt,
        model = model,
    )

    override fun llmProvider(): LLMProvider =
        delegate.llmProvider()

    override fun close() {
        delegate.close()
    }
}