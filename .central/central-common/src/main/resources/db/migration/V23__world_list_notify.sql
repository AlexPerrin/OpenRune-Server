CREATE OR REPLACE FUNCTION world_list_notify_fn() RETURNS trigger AS $$
BEGIN
    PERFORM pg_notify(
        'world_list_events',
        json_build_object(
            'world_id', COALESCE(NEW.world_id, OLD.world_id),
            'op', lower(TG_OP)
        )::text
    );
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS world_list_notify ON worlds;

CREATE TRIGGER world_list_notify
AFTER INSERT OR UPDATE OR DELETE ON worlds
FOR EACH ROW
EXECUTE PROCEDURE world_list_notify_fn();
