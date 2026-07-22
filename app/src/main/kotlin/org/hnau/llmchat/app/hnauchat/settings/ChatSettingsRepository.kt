package org.hnau.llmchat.app.hnauchat.settings

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.hnau.commons.kotlin.foldNullable
import org.hnau.commons.kotlin.mapper.Mapper
import org.hnau.commons.kotlin.mapper.toMapper
import org.hnau.llmchat.app.db.DBAccessor
import org.hnau.llmchat.chat.api.ChatId
import java.sql.PreparedStatement
import java.sql.ResultSet

interface ChatSettingsRepository {

    val settings: ChatSettings

    suspend fun update(
        newSettings: ChatSettings,
    )

    @Suppress("ConstPropertyName")
    companion object {

        suspend fun create(
            db: DBAccessor,
            chatId: ChatId,
        ): ChatSettingsRepository {

            var cached = db.withConnection { connection ->
                connection
                    .prepareStatement("SELECT $SettingsColumn FROM $TableName WHERE $ChatIdColumn = ?")
                    .apply { setString(1, chatId.id) }
                    .use { statement ->
                        statement
                            .executeQuery()
                            .takeIf(ResultSet::next)
                            .foldNullable(
                                ifNull = { ChatSettings() },
                                ifNotNull = { resultSet ->
                                    resultSet
                                        .getString(SettingsColumn)
                                        .let(settingsStringMapper.direct)
                                }
                            )
                    }
            }

            return object : ChatSettingsRepository {
                override val settings: ChatSettings
                    get() = cached

                private val updateMutex = Mutex()

                override suspend fun update(
                    newSettings: ChatSettings,
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

        private const val TableName = "chat_settings"

        private const val ChatIdColumn = "chat_id"

        private const val SettingsColumn = "settings"

        @Suppress("JSON_FORMAT_REDUNDANT")
        private val settingsStringMapper: Mapper<String, ChatSettings> =
            Json { ignoreUnknownKeys = true }.toMapper(ChatSettings.serializer())
    }
}
