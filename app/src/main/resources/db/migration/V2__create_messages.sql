CREATE TABLE messages (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    role TEXT NOT NULL,
    transport_ids TEXT NOT NULL DEFAULT '[]',
    text TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    parent_message_id TEXT,
    summary TEXT
);

CREATE INDEX idx_messages_user_timestamp ON messages(user_id, timestamp);
CREATE INDEX idx_messages_parent_message_id ON messages(parent_message_id);
