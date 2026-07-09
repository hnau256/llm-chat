package org.hnau.llmchat.app.hnauchat.settings

suspend inline fun UserSettingsRepository.update(
    update: UserSettings.() -> UserSettings,
) {
    update(
        newSettings = update(settings)
    )
}