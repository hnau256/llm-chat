package org.hnau.llmchat.app.hnauchat.messages

import org.hnau.llmchat.app.db.DBAccessor
import java.sql.PreparedStatement
import java.sql.ResultSet

data class MessageRecord(
    val id: String,
    val userId: String,
    val transportIds: List<String>,
    val text: String,
    val timestamp: Long,
    val parentMessageId: String?,
    val summary: String?,
)

interface MessagesRepository {

    suspend fun save(record: MessageRecord)

    suspend fun findByTransportId(
        userId: String,
        transportId: String,
    ): String?

    suspend fun findLastMessageId(
        userId: String,
    ): String?

    companion object {

        fun create(
            db: DBAccessor,
        ): MessagesRepository = object : MessagesRepository {

            override suspend fun save(record: MessageRecord) {
                db.withConnection { connection ->
                    connection
                        .prepareStatement(
                            """
                                INSERT INTO $TableName ($IdColumn, $UserIdColumn, $TransportIdsColumn, $TextColumn, $TimestampColumn, $ParentMessageIdColumn, $SummaryColumn)
                                VALUES (?, ?, ?, ?, ?, ?, ?)
                            """.trimIndent()
                        )
                        .apply {
                            setString(1, record.id)
                            setString(2, record.userId)
                            setString(3, encodeTransportIds(record.transportIds))
                            setString(4, record.text)
                            setLong(5, record.timestamp)
                            if (record.parentMessageId != null) setString(6, record.parentMessageId) else setNull(6, java.sql.Types.VARCHAR)
                            if (record.summary != null) setString(7, record.summary) else setNull(7, java.sql.Types.VARCHAR)
                        }
                        .use(PreparedStatement::executeUpdate)
                }
            }

            override suspend fun findByTransportId(
                userId: String,
                transportId: String,
            ): String? = db.withConnection { connection ->
                connection
                    .prepareStatement(
                        """
                            SELECT $IdColumn FROM $TableName
                            WHERE $UserIdColumn = ? AND EXISTS (
                                SELECT 1 FROM json_each($TransportIdsColumn) WHERE value = ?
                            )
                        """.trimIndent()
                    )
                    .apply {
                        setString(1, userId)
                        setString(2, transportId)
                    }
                    .use { statement ->
                        statement
                            .executeQuery()
                            .takeIf(ResultSet::next)
                            ?.getString(IdColumn)
                    }
            }

            override suspend fun findLastMessageId(
                userId: String,
            ): String? = db.withConnection { connection ->
                connection
                    .prepareStatement(
                        """
                            SELECT $IdColumn FROM $TableName
                            WHERE $UserIdColumn = ?
                            ORDER BY $TimestampColumn DESC
                            LIMIT 1
                        """.trimIndent()
                    )
                    .apply { setString(1, userId) }
                    .use { statement ->
                        statement
                            .executeQuery()
                            .takeIf(ResultSet::next)
                            ?.getString(IdColumn)
                    }
            }
        }

        private fun encodeTransportIds(ids: List<String>): String =
            ids.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }

        private const val TableName = "messages"

        private const val IdColumn = "id"
        private const val UserIdColumn = "user_id"
        private const val TransportIdsColumn = "transport_ids"
        private const val TextColumn = "text"
        private const val TimestampColumn = "timestamp"
        private const val ParentMessageIdColumn = "parent_message_id"
        private const val SummaryColumn = "summary"
    }
}
