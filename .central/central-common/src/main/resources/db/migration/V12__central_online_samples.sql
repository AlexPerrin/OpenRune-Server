-- Point-in-time online counts per world (sampled on an interval, separate from activity logs).
CREATE TABLE IF NOT EXISTS online_samples (
    sampled_at BIGINT NOT NULL, -- epoch millis, UTC instant
    world_id INTEGER NOT NULL REFERENCES worlds (world_id) ON DELETE CASCADE,
    online_count INTEGER NOT NULL CHECK (online_count >= 0),
    PRIMARY KEY (sampled_at, world_id)
);

CREATE INDEX IF NOT EXISTS idx_online_samples_world_time
    ON online_samples (world_id, sampled_at DESC);

CREATE INDEX IF NOT EXISTS idx_online_samples_time
    ON online_samples (sampled_at DESC);
