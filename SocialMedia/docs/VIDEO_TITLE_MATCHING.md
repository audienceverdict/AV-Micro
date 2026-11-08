# Automatic application video matching

The existing scheduled sync fetches provider content as before. When it persists a
VIDEO, SHORT_VIDEO, REEL or LIVE upload, it automatically attaches an unassigned
upload to an application video with the same matching title, creating a VID-UUID
only when no match exists. Matching spans all accounts and platforms in this
application library. Images, carousels and stories are excluded.

## Title rules

- Prefer the provider title. When it is missing or whitespace-only, use the first
  nonempty line of the caption/message (including Instagram and Facebook).
- Normalize Unicode using NFKC, lowercase with Locale.ROOT and remove **all Unicode
  whitespace** for matching. Punctuation and other characters remain significant.
- `HI Hello`, `hi     HELLO` and `HIHELLO` share one ID. `Antah karana` and
  `Antahkarana` also share one ID. Different videos with the same matching title
  intentionally share an application video.
- Preserve the first application's display title and description. Matching does
  not replace them with later uploads' metadata.
- Missing titles and titles longer than 255 characters remain unassigned for
  manual review. Titles are not truncated, to avoid false matches.

`POST /api/videos` uses the same find-or-create rule and returns the existing video
on a matching title. The existing response status remains 201. AV post approval
also uses this rule. Existing manual assignment/reassignment APIs remain available.

## Consistency and existing data

The SHA-256 key of the matching title has a unique database index. MySQL upsert and
a locking read resolve simultaneous syncs or manual creates to the same immutable
ID. Content, application video creation, linking, assignment audit and sync
checkpoint changes participate in the existing page transaction.
Sync page writes and standalone find-or-create/automatic assignment transactions
use READ COMMITTED with row locks, avoiding MySQL gap-lock deadlocks when multiple
uploads check currently empty assignment and audit tables at the same time.

Repeated syncs do not move already assigned uploads, including after a title edit.
Explicit manual unlinks remain unlinked on later syncs. Uploads reviewed as MOVIE
are excluded from automatic assignment. Automatic assignments use the existing
assignment audit table. Unchanged uploads are also checked so older unassigned
content can be reconciled when encountered again.

Flyway V11 adds the matching key and audit lookup index. V12 populates keys on
existing application videos and links eligible existing unassigned uploads without
provider API calls. For legacy duplicate matching titles, the oldest record
(`created_at`, then `id`) becomes the match target. Other IDs and their existing
assignments remain intact; their duplicate IDs are logged for operator review.
No existing application video is deleted or merged. These migrations run on the
next application startup; review the duplicate warnings during rollout.

Verification: `mvn -pl SocialMedia verify`. MySQL integration tests require Docker
and use disposable Testcontainers databases; provider calls are mocked.
