# Live comment reply test — 1 November 2026

Facebook and Instagram each delivered one reply. YouTube rejected the reply with HTTP 403 because both configured OAuth tokens lack the comment-writing scope.

Reply text: `Thank you for your comment!`

| Platform | Content | Application status | Provider reply ID | Attempts |
|---|---|---|---|---|
| facebook | [122094711117539941](https://www.facebook.com/photo.php?fbid=122094711117539941&set=a.122094711165539941&type=3) | SENT | 122094711189539941_1457565473148857 | 1 |
| instagram | [18251020576312136](https://www.instagram.com/p/DfKBll6hksT/) | SENT | 18042639677836604 | 1 |
| youtube | [6z_Z-6m_uOk](https://www.youtube.com/watch?v=6z_Z-6m_uOk) | FAILED | None — HTTP 403 | 1 |

## Verification

Every submission used the authenticated application endpoint `POST /api/comments/{id}/replies` with a stable Idempotency-Key. It returned HTTP 202 and a durable job ID. The worker invoked the real provider APIs. Each job was attempted exactly once.

Independent provider GETs returned HTTP 200 for both successful replies. The returned text matched the submitted text. Facebook reported the expected parent comment ID; Instagram listed the reply under the selected comment’s `/replies` edge. The application thread endpoints also returned both SENT jobs and the corresponding locally stored reply comments.

## YouTube permission requirement

Both the refreshed token and separately configured access token were inspected using Google tokeninfo. Their scopes are `youtube.readonly`, `youtube.upload`, and `yt-analytics.readonly`; neither includes `https://www.googleapis.com/auth/youtube.force-ssl`. Reading the selected stored comment and submitting its reply returned HTTP 403. No YouTube reply was posted.

Reauthorize the configured channel with `https://www.googleapis.com/auth/youtube.force-ssl` in addition to the existing required scopes, and update its configured refresh-token grant. After that, resume the existing failed job instead of enqueueing another reply. This authorization must be completed by the account owner.

## Isolated execution and retained records

The temporary application used port `18091` and database `social_reply_live_8b9db60fe41b427f`. Only selected account/content/comment rows were copied from the existing application database. The existing application on port 8080 and its configuration/database were not changed. The temporary instance was stopped after verification. The isolated database is retained for audit and retrying the YouTube job.

| Platform | Local comment ID | Parent provider comment ID | Reply job ID |
|---|---|---|---|
| facebook | 19 | 122094711189539941_2588229161696953 | 44a533a2-046e-44d5-a138-d93c0aee92bb |
| instagram | 1 | 18111355049463789 | 6917cf15-a2a6-4854-b7a9-df2a7cda473c |
| youtube | 2 | UgzXKOBvlcMtnmeWP0Z4AaABAg | 1054e1f3-a3a2-4b4b-a3ff-f689044abc4f |

Sanitized evidence is retained under `data/reply-live-test-2026-11-01/`: `results.json`, `provider-verification.json`, `application-verification.json`, `credential-check.json`, `youtube-access-token-check.json`, `targets.json`, and `state.json`. Credentials and access tokens are excluded from these reports.

The two successful replies were left published. No duplicate submissions or automatic retries were performed.
