INSERT INTO online_samples (sampled_at, world_id, online_count)
VALUES (?, ?, ?)
ON CONFLICT (sampled_at, world_id) DO UPDATE SET online_count = EXCLUDED.online_count
