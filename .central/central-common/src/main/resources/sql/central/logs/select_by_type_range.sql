SELECT id, log_uuid, log_type, occurred_at, account_id, character_id, world_id, payload::text AS payload_json
FROM activity_logs
WHERE log_type = ?
  AND occurred_at >= ?
  AND occurred_at < ?
ORDER BY occurred_at DESC
LIMIT ?
