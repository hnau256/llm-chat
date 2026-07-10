package org.hnau.llmchat.app.hnauchat.llmconnection

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import arrow.core.toOption
import arrow.optics.Lens
import arrow.optics.Prism
import io.ktor.http.Url
import org.hnau.commons.gen.fold.annotations.Fold
import org.hnau.commons.gen.loggable.annotations.Loggable
import org.hnau.commons.gen.pipe.annotations.Pipe
import org.hnau.commons.kotlin.KeyValue
import org.hnau.commons.kotlin.foldBoolean
import org.hnau.commons.kotlin.foldNullable
import org.hnau.commons.kotlin.ifNull
import org.hnau.llmchat.chat.api.ButtonIcon
import org.hnau.llmchat.common.ApiKey
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
        val icon: ButtonIcon,
        val content: Content,
        val set: suspend (String) -> Unit,
    ) {

        @Fold
        sealed interface Content {

            data object NoValue : Content

            data object Sensitive : Content

            data class Value(
                val value: String,
            ) : Content
        }

    }

    val configFields: List<ConfigField>
        get() = config
            ?.foldRaw(
                ifDeepSeek = { config ->
                    listOf(
                        config.createConfigField(
                            id = "apiKey",
                            title = "Api key",
                            icon = ButtonIcon.key,
                            sensitive = true,
                            stringMapper = Prism(
                                getOption = { raw ->
                                    ApiKey
                                        .tryCreate(raw)
                                        .toOption()
                                },
                                reverseGet = ApiKey::value,
                            ),
                            extractor = LLMClientConfig.DeepSeek.apiKey,
                        )
                    )
                },
                ifOllama = { config ->
                    listOf(
                        config.createConfigField(
                            id = "url",
                            title = "Url",
                            icon = ButtonIcon.language,
                            sensitive = false,
                            stringMapper = Prism(
                                getOption = {
                                    Url
                                        .tryParse(
                                            raw = it,
                                            defaultProtocol = "http",
                                        )
                                        .getOrNull()
                                        .toOption()
                                },
                                reverseGet = { url -> url.toString() },
                            ),
                            extractor = LLMClientConfig.Ollama.url,
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
        sensitive: Boolean,
        extractor: Lens<C, T?>,
        stringMapper: Prism<String, T>,
    ): ConfigField = ConfigField(
        id = id,
        title = title,
        icon = icon,
        content = extractor.get(this).foldNullable(
            ifNull = {
                ConfigField.Content.NoValue
            },
            ifNotNull = { value ->
                sensitive.foldBoolean(
                    ifTrue = { ConfigField.Content.Sensitive },
                    ifFalse = {
                        stringMapper
                            .reverseGet(value)
                            .let(ConfigField.Content::Value)
                    }
                )
            }
        ),
        set = { input ->
            dependencies
                .settings
                .update {
                    copy(
                        llmClientConfig = extractor.set(
                            source = this@createConfigField,
                            focus = stringMapper
                                .getOrModify(input)
                                .getOrNull(),
                        )
                    )
                }
        }
    )
}