package org.hnau.llmchat.app.hnauchat.messages

import org.hnau.llmchat.app.db.DBAccessor
import org.hnau.llmchat.chat.api.ChatId
import org.hnau.llmchat.chat.api.MessageId
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlin.time.Instant

data class MessageRecord(
    val id: MessageId,
    val userId: ChatId,
    val transportIds: List<MessageId>,
    val text: String,
    val timestamp: Instant,
    val parentMessageId: MessageId?,
    val summary: String?,
)

interface MessagesRepository {

    suspend fun save(record: MessageRecord)

    suspend fun findByTransportId(
        userId: ChatId,
        transportId: MessageId,
    ): MessageId?

    suspend fun findLastMessageId(
        userId: ChatId,
    ): MessageId?

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
                            setString(1, record.id.id)
                            setString(2, record.userId.id)
                            setString(3, encodeTransportIds(record.transportIds))
                            setString(4, record.text)
                            setLong(5, record.timestamp.toEpochMilliseconds())
                            if (record.parentMessageId != null) setString(6, record.parentMessageId.id) else setNull(6, java.sql.Types.VARCHAR)
                            if (record.summary != null) setString(7, record.summary) else setNull(7, java.sql.Types.VARCHAR)
                        }
                        .use(PreparedStatement::executeUpdate)
                }
            }

            override suspend fun findByTransportId(
                userId: ChatId,
                transportId: MessageId,
            ): MessageId? = db.withConnection { connection ->
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
                        setString(1, userId.id)
                        setString(2, transportId.id)
                    }
                    .use { statement ->
                        statement
                            .executeQuery()
                            .takeIf(ResultSet::next)
                            ?.getString(IdColumn)
                            ?.let(::MessageId)
                    }
            }

            override suspend fun findLastMessageId(
                userId: ChatId,
            ): MessageId? = db.withConnection { connection ->
                connection
                    .prepareStatement(
                        """
                            SELECT $IdColumn FROM $TableName
                            WHERE $UserIdColumn = ?
                            ORDER BY $TimestampColumn DESC
                            LIMIT 1
                        """.trimIndent()
                    )
                    .apply { setString(1, userId.id) }
                    .use { statement ->
                        statement
                            .executeQuery()
                            .takeIf(ResultSet::next)
                            ?.getString(IdColumn)
                            ?.let(::MessageId)
                    }
            }
        }

        private fun encodeTransportIds(ids: List<MessageId>): String =
            ids.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"${it.id}\"" }

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
