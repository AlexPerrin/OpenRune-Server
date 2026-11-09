CREATE TABLE IF NOT EXISTS sessions (
    id BIGSERIAL PRIMARY KEY,
    account_id INTEGER NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    world_id INTEGER NOT NULL REFERENCES worlds (world_id),
    character_id INTEGER NULL REFERENCES account_characters (id) ON DELETE SET NULL,
    token_hash BYTEA NOT NULL UNIQUE,
    created_at BIGINT NOT NULL,
    last_seen_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sessions_account ON sessions (account_id);
CREATE INDEX IF NOT EXISTS idx_sessions_world ON sessions (world_id);
CREATE INDEX IF NOT EXISTS idx_sessions_last_seen ON sessions (last_seen_at);
