SELECT world_id
FROM sessions
WHERE character_id = ?
  AND last_seen_at >= ?
ORDER BY last_seen_at DESC
LIMIT 1
