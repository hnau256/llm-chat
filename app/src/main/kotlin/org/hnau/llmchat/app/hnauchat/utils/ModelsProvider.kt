package org.hnau.llmchat.app.hnauchat.utils

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import korlibs.time.seconds
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.hnau.commons.kotlin.KeyValue
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class ModelsProvider(
    private val errorCacheTime: Duration = 10.seconds,
) {

    private inner class ForClient(
        private val client: LLMClient,
        private val cacheTime: Duration,
    ) {

        private val accessCacheMutex = Mutex()

        private var cache: KeyValue<Instant, Result<List<LLModel>>>? = null

        suspend fun get(): Result<List<LLModel>> = accessCacheMutex.withLock {
            val now = Clock.System.now()
            var result = cache
                ?.takeIf {
                    val cacheTime = it.value.fold(
                        onSuccess = { cacheTime },
                        onFailure = { errorCacheTime },
                    )
                    it.key + cacheTime > now
                }
                ?.value
            if (result == null) {
                result = runCatching { client.models() }
                cache = KeyValue(now, result)
            }
            result
        }

    }

    private val cache: MutableMap<String, ForClient> =
        ConcurrentHashMap()

    suspend fun getModels(
        client: LLMClient,
        cacheTime: Duration,
    ): Result<List<LLModel>> = cache
        .getOrPut(client.clientName) {
            ForClient(
                client = client,
                cacheTime = cacheTime,
            )
        }
        .get()
}