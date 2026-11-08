# Comment access

All comment endpoints require an Identity access JWT. Authenticated users may read:

- `GET /api/comments` (existing filters and cursor pagination)
- `GET /api/comments/{id}/thread`
- `GET /api/videos/{videoId}/comments`

Application-video comment reads do not require a `video_user_assignments` row or
an operator account grant. Any authenticated user can read comments on any existing
application video across its linked YouTube, Facebook and Instagram uploads.
The existing route, response fields, `afterId` cursor and `limit` are unchanged.
Audience assignment routes keep their existing access rules.

Administrators have complete comment access across all accounts, including inbox,
threads, replies, new comments, resolution and delivery-job management. The Identity
JWT must contain the `ADMIN` role. No per-operator account allowlist is required;
legacy `social.replies.operators` configuration does not restrict administrators.
Regular users retain read access only.

## Frontend upload endpoint

`POST /api/v1/social/uploads/{uploadId}/comments` through the gateway (direct service:
`POST /api/uploads/{uploadId}/comments`) accepts `{"text":"Hello"}`. `uploadId` is the
local `social_content.id`. Requires an administrator and returns HTTP 202 with the existing asynchronous job fields. The
`Idempotency-Key` header is optional on this new route; provide a stable key when
retrying a request to prevent duplicates. Without it, each request creates a job.
Browser CORS allows the header. Existing endpoints retain their header requirements.

## New top-level comments

`POST /api/content/{contentId}/comments`, with the local `social_content.id`, an
`Idempotency-Key` header, and JSON `{"text":"Hello"}`, returns HTTP 202 and a durable
job. The job includes `content_id` and a null `comment_id`. Poll the existing
`GET /api/comment-replies/{id}` endpoint. Retry and uncertain-delivery confirmation
use the existing job endpoints and rules. Keys are shared with replies per operator;
reusing a key for a different target or text returns 409.

YouTube, Facebook and Instagram use their platform-specific comment endpoints.
YouTube stores the returned top-level comment ID, allowing subsequent replies to
address that comment. Sent comments appear in stored comment reads with no parent.
Existing reply routes, response fields, thread targeting, idempotency, and delivery
states are preserved.

Run Flyway migration V13 before running the updated worker. Existing reply jobs
remain valid. Both new comments and replies require the platform and account to be enabled.
The legacy `comment-replies-enabled` switch does not restrict administrator jobs.

## Permission failures

A backend 403 on reads should be resolved by supplying a valid user access JWT;
reads no longer require ADMIN. A write 403 requires the ADMIN role. Jobs are authorized when queued;
the worker does not require an additional operator account grant.

Provider permission rejections remain failed jobs and must be fixed at the provider:
a configured account token must authorize comment management. YouTube requires
OAuth comment authorization (not only an API key or upload-only grant); reconnect
with the appropriate scope when required. Meta requires the correct account/Page
access token and comment-management permissions. Revoked, expired or insufficient
provider tokens cannot be repaired by relaxing backend authorization.

Delivery remains asynchronous. UNKNOWN results must be inspected before confirming;
retrying an ambiguous POST can duplicate comments.
