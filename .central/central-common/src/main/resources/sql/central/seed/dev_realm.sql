INSERT INTO realms (
    realm_id, name, description, login_message, login_broadcast, spawn_coord, respawn_coord,
    dev_mode, require_registration, auto_assign_display_names,
    player_xp_rate_in_hundreds, global_xp_rate_in_hundreds
)
VALUES (
    255, 'development', 'Development realm', ?, NULL,
    '0_50_50_21_18', '0_50_50_21_18',
    1, 0, 1,
    15000, 100
)
ON CONFLICT (realm_id) DO NOTHING
