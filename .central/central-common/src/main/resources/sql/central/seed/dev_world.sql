INSERT INTO worlds (
    world_id, flags, host, activity, location, population, sort_order, enabled,
    max_players, world_key_sha256, realm_id,
    login_restrictions_enabled, login_min_total_level, login_min_rights_token,
    login_gate_min_level_enabled, login_gate_rights_enabled, login_gate_whitelist_enabled
)
VALUES (
    255, 'MEMBERS', '127.0.0.1', ?, 0, 0, 0, 1,
    NULL, NULL, 255,
    0, 0, NULL,
    0, 0, 0
)
ON CONFLICT (world_id) DO NOTHING
