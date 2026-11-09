-- Idempotent migration for databases created before `log_uuid` existed on `activity_logs`.
ALTER TABLE activity_logs
    ADD COLUMN IF NOT EXISTS log_uuid UUID;

UPDATE activity_logs
SET log_uuid = gen_random_uuid()
WHERE log_uuid IS NULL;

ALTER TABLE activity_logs
    ALTER COLUMN log_uuid SET DEFAULT gen_random_uuid();

ALTER TABLE activity_logs
    ALTER COLUMN log_uuid SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_activity_logs_log_uuid ON activity_logs (log_uuid);
