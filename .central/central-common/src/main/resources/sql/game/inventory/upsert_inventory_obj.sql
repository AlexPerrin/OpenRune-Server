INSERT INTO inventory_objs (character_id, inv, slot, obj, count, vars)
VALUES (?, ?, ?, ?, ?, ?)
ON CONFLICT(character_id, inv, slot) DO UPDATE SET
    obj = excluded.obj,
    count = excluded.count,
    vars = excluded.vars
