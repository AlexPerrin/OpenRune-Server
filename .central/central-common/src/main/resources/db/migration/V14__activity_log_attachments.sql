-- Links activity log rows to any subject (punishments, tickets, etc.) via (subject_type, subject_id).
-- Must run after `activity_logs` exists (09).
CREATE TABLE IF NOT EXISTS activity_log_attachments (
    subject_type TEXT NOT NULL,
    subject_id BIGINT NOT NULL,
    log_uuid UUID NOT NULL REFERENCES activity_logs (log_uuid) ON DELETE CASCADE,
    PRIMARY KEY (subject_type, subject_id, log_uuid)
);

CREATE INDEX IF NOT EXISTS idx_activity_log_attachments_log_uuid ON activity_log_attachments (log_uuid);

CREATE INDEX IF NOT EXISTS idx_activity_log_attachments_subject ON activity_log_attachments (subject_type, subject_id);
