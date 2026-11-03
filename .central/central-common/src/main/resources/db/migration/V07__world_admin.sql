CREATE TABLE IF NOT EXISTS world_reboot_schedules (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    world_id INTEGER NULL REFERENCES worlds (world_id) ON DELETE CASCADE,
    reboot_at TIMESTAMPTZ NOT NULL,
    message TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'active',
    created_by TEXT NOT NULL DEFAULT 'admin-web',
    cancelled_at TIMESTAMPTZ NULL,
    CONSTRAINT chk_world_reboot_status CHECK (status IN ('active', 'cancelled', 'completed'))
);

CREATE INDEX IF NOT EXISTS idx_world_reboot_active ON world_reboot_schedules (status, reboot_at)
    WHERE status = 'active';

CREATE TABLE IF NOT EXISTS world_broadcast_log (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    world_id INTEGER NULL REFERENCES worlds (world_id) ON DELETE SET NULL,
    message TEXT NOT NULL,
    url TEXT NOT NULL DEFAULT '',
    icon TEXT NOT NULL DEFAULT '',
    created_by TEXT NOT NULL DEFAULT 'admin-web'
);

CREATE OR REPLACE FUNCTION world_reboot_notify_fn() RETURNS trigger AS $$
DECLARE
    payload json;
BEGIN
    IF TG_OP = 'INSERT' AND NEW.status = 'active' THEN
        payload :=
            json_build_object(
                'op', 'set',
                'schedule_id', NEW.id,
                'world_id', NEW.world_id,
                'reboot_at_ms', (EXTRACT(EPOCH FROM NEW.reboot_at) * 1000)::bigint,
                'message', NEW.message
            );
        PERFORM pg_notify('world_reboot_events', payload::text);
    ELSIF TG_OP = 'UPDATE' AND NEW.status = 'cancelled' AND (OLD.status IS DISTINCT FROM NEW.status) THEN
        payload :=
            json_build_object(
                'op', 'clear',
                'schedule_id', NEW.id,
                'world_id', NEW.world_id
            );
        PERFORM pg_notify('world_reboot_events', payload::text);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER world_reboot_notify
AFTER INSERT OR UPDATE ON world_reboot_schedules
FOR EACH ROW
EXECUTE PROCEDURE world_reboot_notify_fn();

CREATE OR REPLACE FUNCTION world_broadcast_notify_fn() RETURNS trigger AS $$
DECLARE
    payload json;
BEGIN
    payload :=
        json_build_object(
            'world_id', NEW.world_id,
            'message', NEW.message,
            'url', NEW.url,
            'icon', NEW.icon
        );
    PERFORM pg_notify('world_broadcast_events', payload::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER world_broadcast_notify
AFTER INSERT ON world_broadcast_log
FOR EACH ROW
EXECUTE PROCEDURE world_broadcast_notify_fn();
