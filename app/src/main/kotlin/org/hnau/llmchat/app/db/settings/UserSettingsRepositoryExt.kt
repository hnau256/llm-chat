package org.hnau.llmchat.app.db.settings

suspend inline fun UserSettingsRepository.update(
    update: suspend UserSettings.() -> UserSettings,
) {
    val settings = get()
    save(update(settings))
}