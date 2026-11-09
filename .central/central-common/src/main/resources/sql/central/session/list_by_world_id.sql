SELECT id, account_id, world_id, character_id, token_hash, created_at, last_seen_at
FROM sessions
WHERE world_id = ?
