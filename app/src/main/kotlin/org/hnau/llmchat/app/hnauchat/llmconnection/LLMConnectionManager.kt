package org.hnau.llmchat.app.hnauchat.llmconnection

import arrow.optics.Lens
import org.hnau.llmchat.app.dto.ApiKey
import org.hnau.llmchat.app.hnauchat.settings.UserSettingsRepository
import org.hnau.llmchat.app.hnauchat.settings.update
import org.hnau.llmchat.app.llm.model.LLMClientConfig
import org.hnau.llmchat.app.llm.model.LLMProviderType
import org.hnau.llmchat.app.llm.model.createBaseConfig
import org.hnau.llmchat.app.llm.model.foldRaw

class LLMConnectionManager(
    private val settings: UserSettingsRepository,
) {

    val config: LLMClientConfig?
        get() = settings.settings.llmClientConfig

    suspend fun selectType(
        type: LLMProviderType,
    ) {
        if (type == config?.type) {
            return
        }
        settings.update {
            copy(
                llmClientConfig = type.createBaseConfig(),
            )
        }
    }

    data class ConfigField(
        val id: String,
        val title: String,
        val filled: Boolean,
        val set: suspend (String) -> Unit,
    )

    val configFields: List<ConfigField>
        get() = config
            ?.foldRaw(
                ifDeepSeek = { config ->
                    listOf(
                        createConfigField(
                            id = "apiKey",
                            title = "Api key",
                            currentConfig = config,
                            decode = ApiKey::tryCreate,
                            prism = Lens(
                                get = LLMClientConfig.DeepSeek::apiKey,
                                set = { config, apiKey ->
                                    config.copy(
                                        apiKey = apiKey,
                                    )
                                },
                            )
                        )
                    )
                }
            )
            .orEmpty()

    private fun <C : LLMClientConfig, T> createConfigField(
        id: String,
        title: String,
        currentConfig: C,
        prism: Lens<C, T?>,
        decode: (String) -> T?,
    ): ConfigField = ConfigField(
        id = id,
        title = title,
        filled = prism.get(currentConfig) != null,
        set = { input ->
            val decoded = decode(input)
            settings.update {
                copy(
                    llmClientConfig = prism.set(currentConfig, decoded)
                )
            }
        }
    )
}