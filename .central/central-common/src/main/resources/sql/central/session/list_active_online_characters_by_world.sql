SELECT character_id, world_id, account_id
FROM sessions
WHERE world_id = ?
  AND character_id IS NOT NULL
  AND character_id > 0
  AND last_seen_at >= ?
