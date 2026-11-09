INSERT INTO inventories (character_id, inv)
VALUES (?, ?)
ON CONFLICT (character_id, inv) DO NOTHING
