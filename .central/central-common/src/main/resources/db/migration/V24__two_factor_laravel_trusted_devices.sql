-- Laravel Fortify 2FA columns (website-compatible) and per-account trusted devices.
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS two_factor_secret TEXT;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS two_factor_recovery_codes TEXT;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS two_factor_confirmed_at TIMESTAMP;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = ANY (current_schemas(true))
            AND table_name = 'accounts'
            AND column_name = 'twofa_secret'
    ) THEN
        UPDATE accounts
        SET two_factor_secret = twofa_secret
        WHERE two_factor_secret IS NULL
            AND twofa_secret IS NOT NULL;

        UPDATE accounts
        SET two_factor_confirmed_at = twofa_last_verified
        WHERE two_factor_confirmed_at IS NULL
            AND twofa_enabled = TRUE
            AND twofa_secret IS NOT NULL;
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS account_trusted_devices (
    account_id INTEGER NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    device_id INTEGER NOT NULL,
    verified_at TIMESTAMP NOT NULL,
    PRIMARY KEY (account_id, device_id)
);

CREATE INDEX IF NOT EXISTS idx_account_trusted_devices_account_id ON account_trusted_devices (account_id);

INSERT INTO account_trusted_devices (account_id, device_id, verified_at)
SELECT id, known_device, COALESCE(twofa_last_verified, CURRENT_TIMESTAMP)
FROM accounts
WHERE known_device IS NOT NULL
ON CONFLICT (account_id, device_id) DO NOTHING;

ALTER TABLE accounts DROP COLUMN IF EXISTS twofa_enabled;
ALTER TABLE accounts DROP COLUMN IF EXISTS twofa_secret;
ALTER TABLE accounts DROP COLUMN IF EXISTS twofa_last_verified;
ALTER TABLE accounts DROP COLUMN IF EXISTS known_device;
