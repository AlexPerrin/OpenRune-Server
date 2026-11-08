CREATE TABLE IF NOT EXISTS activity_logs (
    id BIGSERIAL PRIMARY KEY,
    log_uuid UUID NOT NULL DEFAULT gen_random_uuid(),
    log_type TEXT NOT NULL,
    occurred_at BIGINT NOT NULL, -- epoch millis, UTC instant
    account_id INTEGER NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    -- 0 = no character row (lobby login). No FK so sentinel 0 is valid.
    character_id INTEGER NOT NULL DEFAULT 0,
    world_id INTEGER NULL REFERENCES worlds (world_id) ON DELETE SET NULL,
    payload JSONB NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_activity_logs_log_uuid ON activity_logs (log_uuid);

CREATE INDEX IF NOT EXISTS idx_activity_logs_type_time
    ON activity_logs (log_type, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_activity_logs_character_time
    ON activity_logs (character_id, occurred_at DESC)
    WHERE character_id > 0;

CREATE INDEX IF NOT EXISTS idx_activity_logs_world_time
    ON activity_logs (world_id, occurred_at DESC)
    WHERE world_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_activity_logs_account_time
    ON activity_logs (account_id, occurred_at DESC);
