package org.hnau.llmchat.app.hnauchat.messages

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.hnau.commons.kotlin.mapper.Mapper
import org.hnau.commons.kotlin.mapper.toMapper
import org.hnau.llmchat.app.db.DBAccessor
import org.hnau.llmchat.chat.api.ChatId
import org.hnau.llmchat.chat.api.ChatMessageId
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlin.time.Instant

class MessagesRepository(
    private val chatId: ChatId,
    private val db: DBAccessor,
) {

    suspend fun save(
        id: StorageMessageId,
        record: MessageRecord,
    ) {
        db.withConnection { connection ->
            connection
                .prepareStatement(
                    """
                            INSERT INTO $TableName ($IdColumn, $ChatIdColumn, $RoleColumn, $TransportIdsColumn, $TextColumn, $TimestampColumn, $ParentMessageIdColumn, $SummaryColumn)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent()
                )
                .apply {
                    setString(1, id.id)
                    setString(2, chatId.id)
                    setString(3, record.role.let(MessageRole.stringMapper.reverse))
                    setString(4, record.transportIds.let(transportMessagesIdsStringMapper.reverse))
                    setString(5, record.text)
                    setLong(6, record.timestamp.let(timestampMapper.reverse))
                    record.parentMessageId?.let { setString(7, it.id) }
                    record.summary?.let { setString(8, it) }
                }
                .use(PreparedStatement::executeUpdate)
        }
    }

    suspend fun findByTransportId(
        transportId: ChatMessageId,
    ): StorageMessageId? = db.withConnection { connection ->
        connection
            .prepareStatement(
                """
                        SELECT $IdColumn FROM $TableName
                        WHERE $ChatIdColumn = ? AND EXISTS (
                            SELECT 1 FROM json_each($TransportIdsColumn) WHERE value = ?
                        )
                    """.trimIndent()
            )
            .apply {
                setString(1, chatId.id)
                setString(2, transportId.id)
            }
            .use { statement ->
                statement
                    .executeQuery()
                    .takeIf(ResultSet::next)
                    ?.getString(IdColumn)
                    ?.let(::StorageMessageId)
            }
    }

    suspend fun findLastMessageId(): StorageMessageId? = db.withConnection { connection ->
        connection
            .prepareStatement(
                """
                        SELECT $IdColumn FROM $TableName
                        WHERE $ChatIdColumn = ?
                        ORDER BY $TimestampColumn DESC
                        LIMIT 1
                    """.trimIndent()
            )
            .apply { setString(1, chatId.id) }
            .use { statement ->
                statement
                    .executeQuery()
                    .takeIf(ResultSet::next)
                    ?.getString(IdColumn)
                    ?.let(::StorageMessageId)
            }
    }

    suspend fun getHistory(
        startId: StorageMessageId,
    ): List<MessageRecord> = db.withConnection { connection ->
        connection
            .prepareStatement(
                """
                    WITH RECURSIVE chain AS (
                        SELECT * FROM $TableName WHERE $IdColumn = ?
                        UNION ALL
                        SELECT m.* FROM $TableName m
                        JOIN chain c ON m.$IdColumn = c.$ParentMessageIdColumn
                    )
                    SELECT * FROM chain ORDER BY $TimestampColumn ASC
                """.trimIndent()
            )
            .apply { setString(1, startId.id) }
            .use { statement ->
                val result = mutableListOf<MessageRecord>()
                statement
                    .executeQuery()
                    .use { rs ->
                        while (rs.next()) {
                            result.add(toMessageRecord(rs))
                        }
                    }
                result
            }
    }

    companion object {


        private fun toMessageRecord(
            rs: ResultSet,
        ): MessageRecord = MessageRecord(
            role = rs.getString(RoleColumn).let(MessageRole.stringMapper.direct),
            transportIds = rs.getString(TransportIdsColumn)
                .let(transportMessagesIdsStringMapper.direct),
            text = rs.getString(TextColumn),
            timestamp = rs.getLong(TimestampColumn).let(timestampMapper.direct),
            parentMessageId = rs.getString(ParentMessageIdColumn)?.let(::StorageMessageId),
            summary = rs.getString(SummaryColumn),
        )

        private const val TableName = "messages"

        private const val IdColumn = "id"
        private const val ChatIdColumn = "chat_id"
        private const val RoleColumn = "role"
        private const val TransportIdsColumn = "transport_ids"
        private const val TextColumn = "text"
        private const val TimestampColumn = "timestamp"
        private const val ParentMessageIdColumn = "parent_message_id"
        private const val SummaryColumn = "summary"

        private val timestampMapper: Mapper<Long, Instant> = Mapper(
            direct = Instant::fromEpochMilliseconds,
            reverse = Instant::toEpochMilliseconds
        )

        private val transportMessagesIdsStringMapper: Mapper<String, List<ChatMessageId>> =
            Json.toMapper(ListSerializer(ChatMessageId.serializer()))
    }
}
