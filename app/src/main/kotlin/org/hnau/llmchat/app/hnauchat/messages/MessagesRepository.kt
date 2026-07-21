package org.hnau.llmchat.app.hnauchat.messages

import org.hnau.llmchat.app.db.DBAccessor
import org.hnau.llmchat.chat.api.ChatId
import org.hnau.llmchat.chat.api.MessageId
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlin.time.Instant

enum class MessageRole { USER, ASSISTANT }

data class MessageRecord(
    val id: MessageId,
    val userId: ChatId,
    val role: MessageRole,
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

    suspend fun findById(
        id: MessageId,
    ): MessageRecord?

    companion object {

        fun create(
            db: DBAccessor,
        ): MessagesRepository = object : MessagesRepository {

            override suspend fun save(record: MessageRecord) {
                db.withConnection { connection ->
                    connection
                        .prepareStatement(
                            """
                                INSERT INTO $TableName ($IdColumn, $UserIdColumn, $RoleColumn, $TransportIdsColumn, $TextColumn, $TimestampColumn, $ParentMessageIdColumn, $SummaryColumn)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """.trimIndent()
                        )
                        .apply {
                            setString(1, record.id.id)
                            setString(2, record.userId.id)
                            setString(3, record.role.name)
                            setString(4, encodeTransportIds(record.transportIds))
                            setString(5, record.text)
                            setLong(6, record.timestamp.toEpochMilliseconds())
                            if (record.parentMessageId != null) setString(7, record.parentMessageId.id) else setNull(7, java.sql.Types.VARCHAR)
                            if (record.summary != null) setString(8, record.summary) else setNull(8, java.sql.Types.VARCHAR)
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

            override suspend fun findById(
                id: MessageId,
            ): MessageRecord? = db.withConnection { connection ->
                connection
                    .prepareStatement(
                        """
                            SELECT * FROM $TableName WHERE $IdColumn = ?
                        """.trimIndent()
                    )
                    .apply { setString(1, id.id) }
                    .use { statement ->
                        statement
                            .executeQuery()
                            .takeIf(ResultSet::next)
                            ?.let(::toMessageRecord)
                    }
            }
        }

        private fun toMessageRecord(rs: ResultSet): MessageRecord = MessageRecord(
            id = MessageId(rs.getString(IdColumn)),
            userId = ChatId(rs.getString(UserIdColumn)),
            role = MessageRole.valueOf(rs.getString(RoleColumn)),
            transportIds = emptyList(),
            text = rs.getString(TextColumn),
            timestamp = Instant.fromEpochMilliseconds(rs.getLong(TimestampColumn)),
            parentMessageId = rs.getString(ParentMessageIdColumn)?.let(::MessageId),
            summary = rs.getString(SummaryColumn),
        )

        private fun encodeTransportIds(ids: List<MessageId>): String =
            ids.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"${it.id}\"" }

        private const val TableName = "messages"

        private const val IdColumn = "id"
        private const val UserIdColumn = "user_id"
        private const val RoleColumn = "role"
        private const val TransportIdsColumn = "transport_ids"
        private const val TextColumn = "text"
        private const val TimestampColumn = "timestamp"
        private const val ParentMessageIdColumn = "parent_message_id"
        private const val SummaryColumn = "summary"
    }
}
