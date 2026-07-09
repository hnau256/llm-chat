package org.hnau.llmchat.app.hnauchat.settings

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.hnau.commons.kotlin.foldNullable
import org.hnau.commons.kotlin.mapper.Mapper
import org.hnau.commons.kotlin.mapper.toMapper
import org.hnau.llmchat.app.chat.ChatId
import org.hnau.llmchat.app.db.DBAccessor
import java.sql.PreparedStatement
import java.sql.ResultSet

interface UserSettingsRepository {

    val settings: UserSettings

    suspend fun update(
        newSettings: UserSettings,
    )

    @Suppress("ConstPropertyName")
    companion object {

        suspend fun create(
            db: DBAccessor,
            chatId: ChatId,
        ): UserSettingsRepository {

            var cached = db.withConnection { connection ->
                connection
                    .prepareStatement("SELECT $SettingsColumn FROM $TableName WHERE $ChatIdColumn = ?")
                    .apply { setString(1, chatId.id) }
                    .use { statement ->
                        statement
                            .executeQuery()
                            .takeIf(ResultSet::next)
                            .foldNullable(
                                ifNull = { UserSettings() },
                                ifNotNull = { resultSet ->
                                    resultSet
                                        .getString(SettingsColumn)
                                        .let(settingsStringMapper.direct)
                                }
                            )
                    }
            }

            return object : UserSettingsRepository {
                override val settings: UserSettings
                    get() = cached

                private val updateMutex = Mutex()

                override suspend fun update(
                    newSettings: UserSettings,
                ) {
                    updateMutex.withLock {
                        db.withConnection { connection ->
                            connection
                                .prepareStatement(
                                    """
                                            INSERT INTO $TableName ($ChatIdColumn, $SettingsColumn) VALUES (?, ?)
                                            ON CONFLICT($ChatIdColumn) DO UPDATE SET $SettingsColumn = excluded.$SettingsColumn
                                        """.trimIndent()
                                )
                                .apply {
                                    setString(1, chatId.id)
                                    setString(2, newSettings.let(settingsStringMapper.reverse))
                                }
                                .use(PreparedStatement::executeUpdate)
                        }
                        cached = newSettings
                    }
                }

            }

        }

        private const val TableName = "user_settings"

        private const val ChatIdColumn = "user_id"

        private const val SettingsColumn = "settings"

        @Suppress("JSON_FORMAT_REDUNDANT")
        private val settingsStringMapper: Mapper<String, UserSettings> =
            Json { ignoreUnknownKeys = true }.toMapper(UserSettings.serializer())
    }
}
