SELECT varp AS varp_key, value
FROM character_varps
WHERE character_id = ?
ORDER BY varp_key
