-- Rollback helper: convert payload column back to text
-- WARNING: run only if you need to revert and after taking a backup.

BEGIN;

ALTER TABLE outbox_event
  ALTER COLUMN payload TYPE text
  USING payload::text;

COMMIT;

-- After running this, the db will store the JSON as text. Re-check application mapping before deploy.