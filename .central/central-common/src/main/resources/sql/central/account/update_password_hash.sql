UPDATE accounts
SET password_hash = ?,
    updated_at = ?
WHERE id = ?
