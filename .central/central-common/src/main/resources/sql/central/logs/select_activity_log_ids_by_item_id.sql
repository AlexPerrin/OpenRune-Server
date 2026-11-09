SELECT i.activity_log_id
FROM activity_log_items i
JOIN activity_logs al ON al.id = i.activity_log_id
WHERE i.item_id = ?
ORDER BY al.occurred_at DESC, i.activity_log_id DESC
LIMIT ?
