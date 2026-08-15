-- V3: migrate payload column to jsonb (idempotent plain SQL)
-- This version avoids PL/pgSQL DO blocks so test tooling and ScriptUtils can execute it.
-- It will attempt to ALTER the column using a safe casting expression. If payload contains
-- malformed JSON the statement will fail and the migration will stop so rows can be fixed.

ALTER TABLE IF EXISTS outbox_event
  ALTER COLUMN payload TYPE jsonb
  USING (CASE WHEN payload IS NULL OR trim(payload) = '' THEN 'null'::jsonb ELSE payload::jsonb END);
