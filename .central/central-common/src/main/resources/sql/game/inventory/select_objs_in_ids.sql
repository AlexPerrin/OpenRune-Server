SELECT character_id, inv, slot, obj, count, vars
FROM inventory_objs
WHERE character_id = ? AND inv IN (__IN__)
