SELECT log_uuid
FROM activity_log_attachments
WHERE subject_type = 'punishment' AND subject_id = ?
