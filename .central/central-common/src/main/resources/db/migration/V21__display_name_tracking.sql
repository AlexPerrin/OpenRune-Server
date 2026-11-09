-- Track last display-name change + previous name (OSRS-style rename cooldown / reclaim context).
-- NOTIFY world-link when display_name changes (Central LISTEN → OP_SERVER_DISPLAY_NAME_SYNC).
-- display_name_holds is created in V04__account_characters.sql.

ALTER TABLE account_characters ADD COLUMN IF NOT EXISTS previous_display_name TEXT NULL;
ALTER TABLE account_characters ADD COLUMN IF NOT EXISTS display_name_changed_at BIGINT NULL;

CREATE OR REPLACE FUNCTION account_characters_notify_display_name_fn() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.display_name IS NOT DISTINCT FROM OLD.display_name THEN
        RETURN NEW;
    END IF;
    IF TG_OP = 'INSERT' AND NEW.display_name IS NULL THEN
        RETURN NEW;
    END IF;
    PERFORM pg_notify(
        'character_display_name_events',
        json_build_object(
            'account_id', NEW.account_id::text,
            'character_id', NEW.id::text,
            'display_name', COALESCE(NEW.display_name, ''),
            'previous_display_name',
            CASE WHEN TG_OP = 'UPDATE' THEN COALESCE(OLD.display_name, '') ELSE '' END
        )::text
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS account_characters_notify_display_name ON account_characters;
CREATE TRIGGER account_characters_notify_display_name
AFTER INSERT OR UPDATE OF display_name ON account_characters
FOR EACH ROW
EXECUTE PROCEDURE account_characters_notify_display_name_fn();
