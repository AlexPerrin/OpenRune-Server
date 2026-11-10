CREATE TABLE IF NOT EXISTS punishments (
    id BIGSERIAL PRIMARY KEY,
    scope TEXT NOT NULL,
    account_id INTEGER NULL REFERENCES accounts (id) ON DELETE CASCADE,
    character_id INTEGER NULL REFERENCES account_characters (id) ON DELETE CASCADE,
    kind TEXT NOT NULL,
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    reason TEXT NOT NULL DEFAULT '',
    private_notes TEXT NULL,
    public_notes TEXT NULL,
    issued_by TEXT NOT NULL,
    approved_by TEXT NULL,
    status TEXT NOT NULL DEFAULT 'active',
    repo_link_uuid UUID NULL,
    CONSTRAINT chk_punishments_scope_fk CHECK (
        (scope = 'account' AND account_id IS NOT NULL AND character_id IS NULL)
        OR (scope = 'character' AND character_id IS NOT NULL AND account_id IS NULL)
    ),
    CONSTRAINT chk_punishments_scope_value CHECK (scope IN ('account', 'character')),
    CONSTRAINT chk_punishments_kind CHECK (
        kind IN ('ban', 'temp_ban', 'mute', 'temp_mute', 'locked', 'kick')
    ),
    CONSTRAINT chk_punishments_status CHECK (status IN ('active', 'inactive', 'squashed')),
    CONSTRAINT chk_punishments_kick_scope CHECK (kind <> 'kick' OR scope IN ('account', 'character')),
    CONSTRAINT chk_punishments_locked_scope CHECK (kind <> 'locked' OR scope IN ('account', 'character'))
);

CREATE INDEX IF NOT EXISTS idx_punishments_account ON punishments (account_id) WHERE account_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_punishments_character ON punishments (character_id) WHERE character_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_punishments_status_issued ON punishments (status, issued_at DESC);
CREATE INDEX IF NOT EXISTS idx_punishments_expires ON punishments (expires_at) WHERE expires_at IS NOT NULL;

CREATE OR REPLACE FUNCTION punishments_notify_enforce_ban_fn() RETURNS trigger AS $$
DECLARE
    acc_id bigint;
    chr int;
    enforce boolean;
BEGIN
    enforce := NEW.status = 'active'
        AND NEW.kind IN ('ban', 'temp_ban', 'locked')
        AND (NEW.expires_at IS NULL OR NEW.expires_at > CURRENT_TIMESTAMP);
    IF NOT enforce THEN
        RETURN NEW;
    END IF;
    acc_id := COALESCE(
        NEW.account_id,
        (SELECT ac.account_id FROM account_characters ac WHERE ac.id = NEW.character_id)
    );
    IF acc_id IS NULL THEN
        RETURN NEW;
    END IF;
    chr := CASE WHEN NEW.scope = 'character' AND NEW.character_id IS NOT NULL THEN NEW.character_id ELSE 0 END;
    PERFORM pg_notify(
        'punishment_events',
        json_build_object(
            'account_id', acc_id,
            'character_id', NULLIF(chr, 0),
            'scope', NEW.scope,
            'as_kick', (NEW.kind = 'locked')
        )::text
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER punishments_notify_enforce_ban
AFTER INSERT OR UPDATE ON punishments
FOR EACH ROW
EXECUTE PROCEDURE punishments_notify_enforce_ban_fn();

CREATE OR REPLACE FUNCTION punishments_notify_kick_fn() RETURNS trigger AS $$
DECLARE
    acc_id bigint;
BEGIN
    IF NEW.kind <> 'kick' OR NEW.status <> 'active' THEN
        RETURN NEW;
    END IF;
    acc_id := COALESCE(
        NEW.account_id,
        (SELECT ac.account_id FROM account_characters ac WHERE ac.id = NEW.character_id)
    );
    IF acc_id IS NULL THEN
        RETURN NEW;
    END IF;
    PERFORM pg_notify(
        'punishment_kick_events',
        json_build_object(
            'account_id', acc_id,
            'character_id',
            CASE
                WHEN NEW.scope = 'character' AND NEW.character_id IS NOT NULL THEN NEW.character_id
                ELSE 0
            END
        )::text
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER punishments_notify_kick
AFTER INSERT ON punishments
FOR EACH ROW
EXECUTE PROCEDURE punishments_notify_kick_fn();
