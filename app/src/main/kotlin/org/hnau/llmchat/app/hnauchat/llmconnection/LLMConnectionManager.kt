package org.hnau.llmchat.app.hnauchat.llmconnection

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import arrow.optics.Lens
import co.touchlab.kermit.Logger
import io.ktor.http.Url
import org.hnau.commons.gen.loggable.annotations.Loggable
import org.hnau.commons.gen.pipe.annotations.Pipe
import org.hnau.commons.kotlin.KeyValue
import org.hnau.commons.kotlin.ifNull
import org.hnau.llmchat.app.chat.ButtonIcon
import org.hnau.llmchat.app.dto.ApiKey
import org.hnau.llmchat.app.hnauchat.settings.UserSettingsRepository
import org.hnau.llmchat.app.hnauchat.settings.update
import org.hnau.llmchat.app.hnauchat.utils.ModelsProvider
import org.hnau.llmchat.app.llm.model.LLMClientConfig
import org.hnau.llmchat.app.llm.model.LLMProviderType
import org.hnau.llmchat.app.llm.model.apiKey
import org.hnau.llmchat.app.llm.model.createBaseConfig
import org.hnau.llmchat.app.llm.model.foldRaw
import org.hnau.llmchat.app.llm.model.url
import org.hnau.llmchat.app.utils.tryParse

class LLMConnectionManager(
    private val dependencies: Dependencies,
) {

    @Pipe
    interface Dependencies {

        val settings: UserSettingsRepository

        val modelsProvider: ModelsProvider
    }

    val config: LLMClientConfig?
        get() = dependencies.settings.settings.llmClientConfig

    suspend fun selectType(
        type: LLMProviderType,
    ) {
        if (type == config?.type) {
            return
        }
        dependencies
            .settings
            .update {
                copy(
                    llmClientConfig = type.createBaseConfig(),
                )
            }
    }

    data class ConfigField(
        val id: String,
        val title: String,
        val filled: Boolean,
        val icon: ButtonIcon,
        val set: suspend (String) -> Unit,
    )

    val configFields: List<ConfigField>
        get() = config
            ?.foldRaw(
                ifDeepSeek = { config ->
                    listOf(
                        config.createConfigField(
                            id = "apiKey",
                            title = "Api key",
                            icon = ButtonIcon.key,
                            decode = ApiKey::tryCreate,
                            lens = LLMClientConfig.DeepSeek.apiKey,
                        )
                    )
                },
                ifOllama = { config ->
                    listOf(
                        config.createConfigField(
                            id = "url",
                            title = "Url",
                            icon = ButtonIcon.language,
                            decode = { raw ->
                                Url
                                    .tryParse(
                                        raw = raw,
                                        defaultProtocol = "http",
                                    )
                                    .getOrNull()
                            },
                            lens = LLMClientConfig.Ollama.url,
                        )
                    )
                },
            )
            .orEmpty()

    data class ModelsItem(
        val model: LLModel,
        val selected: Boolean,
    )

    @Loggable
    inner class Client(
        private val config: LLMClientConfig,
        private val clientType: LLMProviderType,
        private val client: LLMClient,
    ) {

        private suspend fun getClientWithModels(): KeyValue<LLMClient, List<ModelsItem>> {

            val selectedModelId = dependencies
                .settings
                .settings
                .model
                ?.takeIf { it.key == clientType }
                ?.value

            val models = dependencies
                .modelsProvider
                .getModels(
                    client = client,
                    cacheTime = config.type.modelsListCacheTime,
                )
                .getOrElse { error ->
                    logger.w("Unable get models for client $client", error)
                    null
                }
                .orEmpty()
                .map { llmModel ->
                    ModelsItem(
                        model = llmModel,
                        selected = llmModel.id == selectedModelId,
                    )
                }

            return KeyValue(
                key = client,
                value = models,
            )
        }

        suspend fun getModels(): List<ModelsItem> =
            getClientWithModels().value

        suspend fun getClientWithModel(): KeyValue<LLMClient, LLModel>? =
            getClientWithModels().let { (client, models) ->

                val model = models
                    .firstOrNull(ModelsItem::selected)
                    .ifNull { models.firstOrNull() }
                    ?.model
                    ?: return@let null

                KeyValue(
                    key = client,
                    value = model,
                )
            }

        suspend fun setModel(
            model: LLModel,
        ) {
            dependencies
                .settings
                .update {
                    copy(
                        model = KeyValue(
                            key = clientType,
                            value = model.id,
                        )
                    )
                }
        }
    }

    val client: Client?
        get() = config?.run {
            val client = tryCreateLLMClient() ?: return@run null
            Client(
                config = this,
                clientType = type,
                client = client,
            )
        }

    private fun <C : LLMClientConfig, T> C.createConfigField(
        id: String,
        title: String,
        icon: ButtonIcon,
        lens: Lens<C, T?>,
        decode: (String) -> T?,
    ): ConfigField = ConfigField(
        id = id,
        title = title,
        icon = icon,
        filled = lens.get(this) != null,
        set = { input ->
            val decoded = decode(input)
            Logger.w("QWERTY. decoded = $decoded")
            dependencies
                .settings
                .update {
                    copy(
                        llmClientConfig = lens.set(
                            source = this@createConfigField,
                            focus = decoded,
                        )
                    )
                }
        }
    )
}