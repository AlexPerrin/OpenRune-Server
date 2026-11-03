SELECT r.realm_id, r.login_message, r.login_broadcast, r.spawn_coord, r.respawn_coord,
    r.dev_mode, r.require_registration, r.auto_assign_display_names,
    r.player_xp_rate_in_hundreds, r.global_xp_rate_in_hundreds
FROM worlds w
INNER JOIN realms r ON r.realm_id = w.realm_id
WHERE w.world_id = ?
