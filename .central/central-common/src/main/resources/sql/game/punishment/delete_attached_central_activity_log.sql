DELETE FROM activity_log_attachments
WHERE subject_type = 'punishment' AND subject_id = ? AND log_uuid = ?::uuid
