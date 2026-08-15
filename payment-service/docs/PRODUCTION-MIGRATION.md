Production migration checklist for payload -> jsonb

1) Backup production DB (snapshot + logical dump).
2) Run scripts/preview_payload_migration.sql on a staging copy of prod to find malformed payload rows.
   - Fix or remove malformed rows manually, or export them for inspection.
3) Ensure application code deployed has the JsonNode mapping and hibernate-types dependency (this repo contains V3 migration and mapping).
4) Deploy application to a staging environment and let Flyway run migrations (V3 will attempt an idempotent conversion).
5) Confirm outbox_event.payload is jsonb and validate sample rows.
6) Deploy to production during a maintenance window; monitor logs and have rollback steps ready.

Rollback plan:
- If conversion causes issues, restore DB from backup. Alternatively, use payment-service/scripts/rollback_payload_to_text.sql to convert back (requires caution).

CI requirements:
- CI runners must use Java 21.
- Docker must be available for Testcontainers-based integration tests.

Contact:
- DBAs and platform engineers must review the preview output and approve running V3 on prod.