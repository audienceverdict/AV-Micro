# Cutover of the existing application

Do this first against a restored staging database. Do not run the fresh-install Compose stack against the current production schema.

1. Back up the original database and record row counts, user IDs, roles and verifier assignments. Export schema definitions including indexes and constraints. Freeze account and booking writes for cutover.
2. Provision two schemas with reviewed DDL exported from the existing installation. Identity owns users, user_roles, user_verifier_shows, role_assignment_audits, otp_verifications (use the actual @Table name), and auth_locks. AV owns the remaining business tables. Inspect actual table names against the JPA entities before exporting. Do not copy the old Flyway history into both schemas: its V4 identity migration no longer belongs to AV. If the source already completed catalog V1–V3, verify the exported catalog schema and data, then baseline the restored AV schema at version 3 using `--spring.flyway.baseline-version=3` on its first startup. For older source schemas, apply/reconcile the missing catalog migrations in staging first. Identity has no Flyway runner in this initial split; preserve the original migration history with the backup.
3. Copy identity-owned tables preserving all UUIDs, contacts, enabled flags and timestamps. Copy business tables preserving userId references and all existing booking, payment, layout, show, seat and ticket records. Remove cross-schema foreign keys if any; retain indexes. There are no shared runtime JPA entities across databases.
4. Provision the same provider credentials, WhatsApp template, email identity and configured administrator contacts. Generate persistent RSA signing keys and mount the private key only in Identity. Never generate replacement keys on every restart.
5. Run both services with prod profiles (Hibernate validate). Confirm record counts and sample role/show assignments. Do not use DDL_AUTO=update as an unattended production migration.
6. Verify WhatsApp registration, bound email verification, email login, profile access/update, admin role/status changes, verifier restrictions, movie browsing, seat holds, booking/cancellation, payment processing and email notifications through Gateway.
7. Switch the frontend base URL/reverse proxy to Gateway. Existing URLs are retained. Users must log in again because old HS256 access tokens are not accepted by the RSA business verifier. Do not promise seamless acceptance of old JWTs.
8. Keep the old database and application intact until acceptance. If rollback is required after writes resume, reconcile the new writes before reverting; restoring only the old application loses those writes. A maintenance window avoids dual-write complexity.

Private show validation is a remote read before the Identity assignment transaction completes. Show deletion can race assignment, as it could in the original application; access checks always resolve the current show in AV.

No provider credentials from the original configuration files have been copied. Re-enter them through the environment. Local setup does not modify the source project or original databases.
