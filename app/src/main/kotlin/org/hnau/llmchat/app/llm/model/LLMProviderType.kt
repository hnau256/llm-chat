package org.hnau.llmchat.app.llm.model

import korlibs.time.days
import org.hnau.commons.gen.fold.annotations.Fold
import kotlin.time.Duration

@Fold
enum class LLMProviderType(
    val modelsListCacheTime: Duration,
) {

    DeepSeek(
        modelsListCacheTime = cacheTimeLong,
    )
}

private val cacheTimeLong: Duration
    get() = 1.days