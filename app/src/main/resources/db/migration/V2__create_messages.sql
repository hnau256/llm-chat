CREATE TABLE messages (
    id TEXT PRIMARY KEY,
    chat_id TEXT NOT NULL,
    role TEXT NOT NULL,
    transport_ids TEXT NOT NULL DEFAULT '[]',
    text TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    parent_message_id TEXT DEFAULT null,
    summary TEXT DEFAULT null
);

CREATE INDEX idx_messages_chat_timestamp ON messages(chat_id, timestamp);
CREATE INDEX idx_messages_parent_message_id ON messages(parent_message_id);
