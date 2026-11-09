DELETE FROM inventories
WHERE character_id = ? AND inv NOT IN (__IN__)
