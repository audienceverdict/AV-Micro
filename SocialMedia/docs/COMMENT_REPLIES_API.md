# Comment inbox and outbound reply APIs

This backend exposes authenticated APIs for listing comments, retrieving conversations, submitting replies, checking delivery history, retrying failed replies, confirming uncertain deliveries, and resolving or reopening comments. A separate frontend can consume these endpoints.

## Enable access and sending

Provide configuration externally. All operator endpoints require an Identity ADMIN access token in `Authorization: Bearer <token>`. Reply account allowlists are keyed by the token subject (Identity user ID); an absent allowlist grants no account access. See the root deployment instructions in [README](../README.md).

```yaml
social:
  replies:
    enabled: true
    interval-ms: 15000
    operators:
      "IDENTITY_USER_ID":
        account-ids: [1, 2] # local social_accounts.id values
```

Add `comment-replies-enabled: true` to each existing configured account permitted to send replies. The default is false. The platform and account must also be enabled. Operators with no configured account IDs have no access. The worker rechecks operator access and account enablement before sending. `social.replies.enabled=false` pauses delivery but still permits queued submissions and reading.

Use the existing credential references. YouTube needs OAuth for the destination channel with `https://www.googleapis.com/auth/youtube.force-ssl`; an API key or upload-only scope is insufficient. Existing refresh-token configuration is reused. Facebook uses the configured Page access token; Instagram uses the existing Facebook Login professional-account setup and its comment-management permissions. Provider authorization and thread restrictions are ultimately enforced by the provider. Meta permission/app-review compatibility must be checked with the deployed API version and account before enabling live sends.

## Endpoints

All paths below start with `/api` internally or `/api/v1/social` through the gateway. Authenticate as an operator with access to the comment's local account.

| Method | Path | Description |
|---|---|---|
| GET | `/comments?platform=youtube&accountId=1&videoId=VID-...&needsReply=true&afterId=0&limit=50` | Unified inbox; filters optional; limit 1–200 |
| GET | `/comments/{id}/thread` | Selected comment, locally stored thread and outbound history |
| POST | `/comments/{id}/replies` | Body `{"text":"Thank you!"}`; required `Idempotency-Key`; returns 202 with durable job |
| GET | `/comment-replies/{id}` | Delivery status and audit fields |
| POST | `/comment-replies/{id}/retry` | Requeue a definite failure after its provider cooldown |
| POST | `/comment-replies/{id}/confirm` | Body `{"externalReplyId":"provider-id"}`; operator attests an UNKNOWN reply exists on the provider; records SENT without sending |
| PUT | `/comments/{id}/resolution` | Body `{"resolved":true}`; false reopens; independent of delivery |

A request key is unique per operator. Repeating the same target/text/key returns the same job. Reusing a key for another target or text returns 409. Replies must contain 1–10,000 characters; provider-specific limits may be stricter. The reply account and platform come from stored content, never from caller-supplied destination IDs. Replies to existing child replies target the stored root thread, matching the current flat reply model.

Inbox cursors traverse local IDs; restart from zero to refresh edits and delivery changes. `needsReply` means unresolved comments without a SENT outbound job targeting that comment, excluding comments whose author matches the connected account. It cannot reliably infer operator replies made outside this application, especially where provider author IDs are unavailable. A queued or failed reply does not count as delivered. Resolve/reopen is per selected comment.

## Delivery and recovery

Flyway V8 adds reply jobs and resolution fields. Jobs follow QUEUED → SENDING → SENT, FAILED or UNKNOWN. MySQL row locks and SKIP LOCKED prevent concurrent workers claiming the same job. HTTP calls occur outside database transactions. Provider ID, SENT status and a visible local comment commit atomically. Existing sync subsequently upserts the same provider comment.

A definite rejection is FAILED; Retry-After from an explicit rate rejection sets a retry cooldown. Permission failures require correcting credentials before manual retry. No failures automatically resend. Transport errors, 5xx, malformed success responses, or uncertain persistence become UNKNOWN. Interrupted SENDING jobs become UNKNOWN after one hour, exceeding the HTTP client's maximum configured request timeout. UNKNOWN jobs cannot be retried. Inspect the native platform and use confirmation only for an already observed reply; confirmation is an operator attestation, not remote verification. If no delivered reply can be established, retain UNKNOWN for review rather than automatically resending.

Audit fields include submitting actor, timestamps, attempt count, remote reply ID, and confirming actor. Bodies/tokens are not logged. Existing collection continues to retrieve edits and replies. No live provider sends are part of automated verification.

Provider references: [YouTube comments.insert](https://developers.google.com/youtube/v3/docs/comments/insert), [Meta comment moderation](https://developers.facebook.com/docs/instagram-platform/instagram-api-with-facebook-login/comment-moderation/), [Facebook object comments](https://developers.facebook.com/docs/graph-api/reference/object/comments/).

## Verification performed

- `mvn verify`: 70 unit/HTTP adapter/controller tests passed. Docker-based integration tests were skipped because no container runtime was available.
- `mvn -Dit.test=LocalMySqlIT verify` against a dedicated native MySQL database: all 12 integration tests passed, including Flyway V8, reply idempotency, account isolation, durable SENT/local-comment persistence, resolution, interrupted delivery, manual reconciliation, rate-limit cooldowns and no resend after transport ambiguity.
- Packaged application on an isolated local port: anonymous inbox API returned 401, authenticated operator with no accounts received an empty inbox with Cache-Control: no-store.

Provider calls used WireMock; no public comments were posted during testing.
