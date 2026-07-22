package org.hnau.llmchat.app.hnauchat.messages

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.hnau.commons.kotlin.KeyValue
import org.hnau.commons.kotlin.mapper.Mapper
import org.hnau.commons.kotlin.mapper.nameToEnum
import org.hnau.commons.kotlin.mapper.toMapper
import org.hnau.llmchat.app.db.DBAccessor
import org.hnau.llmchat.chat.api.ChatId
import org.hnau.llmchat.chat.api.MessageId
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlin.time.Instant

enum class MessageRole {
    User, Assistant;

    companion object {

        val stringMapper: Mapper<String, MessageRole> =
            Mapper.nameToEnum<MessageRole>()
    }
}

data class MessageRecord(
    val userId: ChatId,
    val role: MessageRole,
    val transportIds: List<MessageId>,
    val text: String,
    val timestamp: Instant,
    val parentMessageId: MessageId?,
    val summary: String?,
)

class MessagesRepository(
    private val db: DBAccessor,
) {

    suspend fun save(
        id: MessageId,
        record: MessageRecord,
    ) {
        db.withConnection { connection ->
            connection
                .prepareStatement(
                    """
                            INSERT INTO $TableName ($IdColumn, $UserIdColumn, $RoleColumn, $TransportIdsColumn, $TextColumn, $TimestampColumn, $ParentMessageIdColumn, $SummaryColumn)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent()
                )
                .apply {
                    setString(1, id.id)
                    setString(2, record.userId.id)
                    setString(3, record.role.let(MessageRole.stringMapper.reverse))
                    setString(4, record.transportIds.let(messagesIdsStringMapper.reverse))
                    setString(5, record.text)
                    setLong(6, record.timestamp.let(timestampMapper.reverse))
                    record.parentMessageId?.let { setString(7, it.id) }
                    record.summary?.let { setString(8, it) }
                }
                .use(PreparedStatement::executeUpdate)
        }
    }

    suspend fun findByTransportId(
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

    suspend fun findLastMessageId(
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

    suspend fun findById(
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
                    ?.value
            }
    }

    companion object {


        private fun toMessageRecord(
            rs: ResultSet,
        ): KeyValue<MessageId, MessageRecord> = KeyValue(
            key = MessageId(rs.getString(IdColumn)),
            value = MessageRecord(
                userId = ChatId(rs.getString(UserIdColumn)),
                role = rs.getString(RoleColumn).let(MessageRole.stringMapper.direct),
                transportIds = rs.getString(TransportIdsColumn).let(messagesIdsStringMapper.direct),
                text = rs.getString(TextColumn),
                timestamp = rs.getLong(TimestampColumn).let(timestampMapper.direct),
                parentMessageId = rs.getString(ParentMessageIdColumn)?.let(::MessageId),
                summary = rs.getString(SummaryColumn),
            ),
        )

        private const val TableName = "messages"

        private const val IdColumn = "id"
        private const val UserIdColumn = "user_id"
        private const val RoleColumn = "role"
        private const val TransportIdsColumn = "transport_ids"
        private const val TextColumn = "text"
        private const val TimestampColumn = "timestamp"
        private const val ParentMessageIdColumn = "parent_message_id"
        private const val SummaryColumn = "summary"

        private val timestampMapper: Mapper<Long, Instant> = Mapper(
            direct = Instant::fromEpochSeconds,
            reverse = Instant::epochSeconds
        )

        private val messagesIdsStringMapper = Json.toMapper(ListSerializer(MessageId.serializer()))
    }
}
