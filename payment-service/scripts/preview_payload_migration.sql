-- Preview payload migration: find rows where payload cannot be cast to jsonb
-- Run this on a copy or staging DB before applying V3 migration.

DO $$
DECLARE
    rec RECORD;
    bad_count INT := 0;
BEGIN
    RAISE NOTICE 'Scanning outbox_event.payload for invalid JSON...';
    FOR rec IN SELECT id, payload FROM outbox_event LOOP
        BEGIN
            PERFORM rec.payload::jsonb;
        EXCEPTION WHEN others THEN
            RAISE NOTICE 'Invalid JSON at id=%: %', rec.id, SQLERRM;
            bad_count := bad_count + 1;
        END;
    END LOOP;
    RAISE NOTICE 'Scan complete. Found % invalid payload(s).', bad_count;
END
$$;