package org.hnau.llmchat.app.llm

import kotlinx.serialization.json.Json
import org.hnau.llmchat.app.chat.dto.UserId
import org.hnau.llmchat.app.db.DBAccessor

class UserSettingsRepository(
    private val db: DBAccessor,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(userId: UserId): UserSettings = db.withConnection { connection ->
        connection
            .prepareStatement("SELECT settings FROM user_settings WHERE user_id = ?")
            .apply { setString(1, userId.userId) }
            .use { statement ->
                val resultSet = statement.executeQuery()
                if (resultSet.next()) {
                    json.decodeFromString(resultSet.getString("settings"))
                } else {
                    UserSettings()
                }
            }
    }

    suspend fun save(userId: UserId, settings: UserSettings) = db.withConnection { connection ->
        connection
            .prepareStatement(
                """
                INSERT INTO user_settings (user_id, settings) VALUES (?, ?)
                ON CONFLICT(user_id) DO UPDATE SET settings = excluded.settings
                """.trimIndent()
            )
            .apply {
                setString(1, userId.userId)
                setString(2, json.encodeToString(UserSettings.serializer(), settings))
            }
            .use { it.executeUpdate() }
    }
}
