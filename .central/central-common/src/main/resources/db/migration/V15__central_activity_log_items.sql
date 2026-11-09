-- Pivot for item lines on trade and single-item move logs. Query by item_id without scanning JSON payload.
CREATE TABLE IF NOT EXISTS activity_log_items (
    activity_log_id BIGINT NOT NULL REFERENCES activity_logs (id) ON DELETE CASCADE,
    slot_key TEXT NOT NULL CHECK (
        slot_key IN (
            'trade_initiated',
            'trade_receiving',
            'pickup_item',
            'dropped_item',
            'destroy_item'
        )
    ),
    line_index SMALLINT NOT NULL CHECK (line_index >= 0),
    item_id TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (activity_log_id, slot_key, line_index)
);

CREATE INDEX IF NOT EXISTS idx_activity_log_items_item_time
    ON activity_log_items (item_id, activity_log_id DESC);
