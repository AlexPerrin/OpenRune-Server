SELECT w.world_id,
       w.enabled,
       w.max_players,
       w.world_key_sha256,
       w.login_restrictions_enabled,
       w.login_min_total_level,
       w.login_min_rights_token,
       w.login_gate_min_level_enabled,
       w.login_gate_rights_enabled,
       w.login_gate_whitelist_enabled,
       COALESCE(r.dev_mode, 0) <> 0 AS realm_dev_mode
FROM worlds w
JOIN realms r ON r.realm_id = w.realm_id
WHERE w.world_id = ?
