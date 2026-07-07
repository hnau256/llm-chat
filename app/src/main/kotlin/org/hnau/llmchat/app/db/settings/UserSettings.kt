package org.hnau.llmchat.app.db.settings

import kotlinx.serialization.Serializable

@Serializable
data class UserSettings(
    val basePrompt: String = "",
)
