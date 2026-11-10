# Account self-deletion

1. Authenticate and POST `/api/v1/auth/me/deletion-otp` (Bearer token, no body).
   A purpose-specific confirmation OTP is emailed to the account's verified email.
2. DELETE `/api/v1/auth/me` with the same Bearer token and `X-Deletion-OTP: <code>`.
   Returns **204 No Content** after committing identity deletion.

Only the authenticated account can be deleted; no user ID is accepted. An ordinary
login OTP cannot confirm deletion. Existing OTP expiry, attempt and resend limits
apply. Missing confirmation header returns 400; invalid/expired codes return 400;
rate limits return 429. Unauthenticated requests return 401. Administrator accounts
are currently excluded (409 ADMIN_DELETION_UNSUPPORTED); transfer the primary admin
role before self-deleting. Accounts without a verified email receive 409.

Deletion disables the account, records deletedAt, clears name/contact details and
verification timestamps, removes staff roles/show assignments, and removes OTP
records for the old contacts. It retains the old user ID. Normal admin lists and
mutations exclude deleted identities. Existing JWTs fail the enabled-account check,
including av-service's identity lookup. Retrying deletion with the old token returns
401. No restoration path is provided.

Mobile/email are released in the same transaction. Fresh registration requires new
mobile and email OTP verification, creates a new user ID, and cannot access the old
account's bookings or inherit its roles. Registration proofs from before deletion
are invalid because the associated OTP records no longer exist.

## Database rollout

Identity currently uses Hibernate ddl-auto=update by default and has Flyway disabled.
For existing databases, apply `scripts/sql/identity-account-soft-deletion.sql`
once before deployment; do not rely on Hibernate to relax the existing NOT NULL constraint. It makes mobile nullable and adds deleted_at and the optimistic
locking version column. Test the migration on a database copy. Existing unique indexes
on mobile/email remain in place; MySQL permits multiple NULL values.

## Retained domain records

This implements identity soft deletion, not erasure of all personal data. Historical
bookings (including copied contact fields), reviews, notifications and other av-service
records remain unchanged and linked to the old ID. Active bookings/refunds are not
cancelled. Cross-service retention, anonymization and pending-delivery cancellation
need a separate agreed policy and implementation. Do not describe this API as complete
data erasure to users.
