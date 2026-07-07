package org.hnau.llmchat.app.db.settings

suspend inline fun UserSettingsRepository.update(
    update: UserSettings.() -> UserSettings,
) {
    update(
        newSettings = update(settings)
    )
}