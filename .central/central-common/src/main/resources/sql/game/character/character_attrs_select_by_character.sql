SELECT attr AS attr_key, value_json
FROM character_attrs
WHERE character_id = ?
ORDER BY attr_key
