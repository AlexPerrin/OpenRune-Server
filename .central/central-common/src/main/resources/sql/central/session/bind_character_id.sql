UPDATE sessions
SET character_id = ?
WHERE id = ?
  AND character_id IS NULL
