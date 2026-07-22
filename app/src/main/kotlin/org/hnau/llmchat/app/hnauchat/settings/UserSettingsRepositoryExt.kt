package org.hnau.llmchat.app.hnauchat.settings

suspend inline fun ChatSettingsRepository.update(
    update: ChatSettings.() -> ChatSettings,
) {
    update(
        newSettings = update(settings)
    )
}