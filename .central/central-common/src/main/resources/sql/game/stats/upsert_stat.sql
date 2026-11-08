INSERT INTO stats (character_id, stat_id, vis_level, base_level, fine_xp)
VALUES (?, ?, ?, ?, ?)
ON CONFLICT(character_id, stat_id) DO UPDATE SET
    vis_level = excluded.vis_level,
    base_level = excluded.base_level,
    fine_xp = excluded.fine_xp,
    updated_at = CASE
        WHEN stats.fine_xp != excluded.fine_xp THEN CURRENT_TIMESTAMP
        ELSE stats.updated_at
    END
