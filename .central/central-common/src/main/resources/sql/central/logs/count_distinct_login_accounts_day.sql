SELECT COUNT(DISTINCT account_id)
FROM activity_logs
WHERE log_type = 'login'
  AND account_id IS NOT NULL
  AND occurred_at >= ?
  AND occurred_at < ?
