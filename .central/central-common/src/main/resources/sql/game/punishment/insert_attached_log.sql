INSERT INTO activity_log_attachments (subject_type, subject_id, log_uuid)
SELECT 'punishment', ?, ?::uuid
WHERE EXISTS (SELECT 1 FROM activity_logs c WHERE c.log_uuid = ?::uuid)
ON CONFLICT (subject_type, subject_id, log_uuid) DO NOTHING
