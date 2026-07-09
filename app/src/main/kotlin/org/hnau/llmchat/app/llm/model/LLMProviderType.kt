package org.hnau.llmchat.app.llm.model

import korlibs.time.days
import korlibs.time.minutes
import org.hnau.commons.gen.fold.annotations.Fold
import kotlin.time.Duration

@Fold
enum class LLMProviderType(
    val modelsListCacheTime: Duration,
) {

    DeepSeek(
        modelsListCacheTime = cacheTimeLong,
    ),

    Ollama(
        modelsListCacheTime = cacheTimeShort,
    )
}

private val cacheTimeShort: Duration
    get() = 1.minutes

private val cacheTimeLong: Duration
    get() = 1.days