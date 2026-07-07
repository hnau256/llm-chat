package org.hnau.llmchat.app.db.settings

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.hnau.commons.kotlin.foldNullable
import org.hnau.commons.kotlin.mapper.Mapper
import org.hnau.commons.kotlin.mapper.toMapper
import org.hnau.llmchat.app.db.DBAccessor
import org.hnau.llmchat.app.dto.UserId
import java.sql.PreparedStatement
import java.sql.ResultSet

class UserSettingsRepository(
    private val db: DBAccessor,
    private val userId: UserId,
) {

    private val cachedAccessMutex = Mutex()

    private var cached: UserSettings? = null

    suspend fun get(): UserSettings = cachedAccessMutex.withLock {
        var result = cached
        if (result == null) {
            result = read()
            cached = result
        }
        result
    }

    private suspend fun read(): UserSettings = db.withConnection { connection ->
        connection
            .prepareStatement("SELECT $SettingsColumn FROM $TableName WHERE $UserIdColumn = ?")
            .apply { setString(1, userId.value) }
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

    suspend fun save(
        settings: UserSettings,
    ) {
        cachedAccessMutex.withLock {
            write(settings)
            cached = settings
        }
    }

    private suspend fun write(
        settings: UserSettings,
    ) {
        db.withConnection { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO $TableName ($UserIdColumn, $SettingsColumn) VALUES (?, ?)
                    ON CONFLICT($UserIdColumn) DO UPDATE SET $SettingsColumn = excluded.$SettingsColumn
                    """.trimIndent()
                )
                .apply {
                    setString(1, userId.value)
                    setString(2, settings.let(settingsStringMapper.reverse))
                }
                .use(PreparedStatement::executeUpdate)
        }
    }

    @Suppress("ConstPropertyName")
    companion object {

        private const val TableName = "user_settings"

        private const val UserIdColumn = "user_id"

        private const val SettingsColumn = "settings"

        @Suppress("JSON_FORMAT_REDUNDANT")
        private val settingsStringMapper: Mapper<String, UserSettings> =
            Json { ignoreUnknownKeys = true }.toMapper(UserSettings.serializer())
    }
}
