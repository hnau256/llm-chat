package org.hnau.llmchat.app.hnauchat.utils

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import korlibs.time.minutes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.hnau.commons.kotlin.KeyValue
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class ModelsProvider(
    private val cacheTime: Duration = 1.minutes,
) {

    private inner class ForClient(
        private val client: LLMClient,
    ) {

        private val accessCacheMutex = Mutex()

        private var cache: KeyValue<Instant, List<LLModel>>? = null

        suspend fun get(): List<LLModel> = accessCacheMutex.withLock {
            val now = Clock.System.now()
            var result = cache
                ?.takeIf { it.key + cacheTime > now }
                ?.value
            if (result == null) {
                result = client.models()
                cache = KeyValue(now, result)
            }
            result
        }

    }

    private val cache: MutableMap<String, ForClient> =
        ConcurrentHashMap()

    suspend fun getModels(
        client: LLMClient,
    ): Result<List<LLModel>> = cache
        .getOrPut(client.clientName) {
            ForClient(
                client = client,
            )
        }
        .runCatching { get() }
}