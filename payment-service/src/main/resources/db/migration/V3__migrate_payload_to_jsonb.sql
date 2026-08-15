-- V3: migrate payload column to jsonb if needed
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'outbox_event'
          AND column_name = 'payload'
          AND data_type <> 'jsonb'
    ) THEN
        -- Cast existing textual payload to jsonb; if payload is already JSON text this will succeed
        ALTER TABLE outbox_event
          ALTER COLUMN payload TYPE jsonb
          USING (CASE WHEN payload IS NULL OR trim(payload) = '' THEN 'null'::jsonb ELSE payload::jsonb END);
    END IF;
EXCEPTION WHEN others THEN
    -- If conversion fails, raise a clear error so migration can be inspected and rolled back
    RAISE NOTICE 'Could not convert outbox_event.payload to jsonb: %', SQLERRM;
    RAISE;
END
$$;