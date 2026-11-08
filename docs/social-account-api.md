# Social account API contract — milestone 1

Service: `av-social-account-service`.

Status: **Historical contract only.** The social-account application has been removed from this repository. This endpoint is not implemented by the current SocialMedia service. See [SocialMedia](../SocialMedia/README.md) for the supported application and APIs.
 This contract precedes the Spring Boot application, Maven module, gateway route and Docker Compose configuration.

## Request and response

```http
GET /api/v1/social/accounts
Authorization: Bearer <existing-av-access-token>
Accept: application/json
```

No request body or query parameters are supported. In particular, reject `userId` with `400 BAD_REQUEST` after authentication; never use it to select an owner. The owner is the nonblank string `sub` of the validated JWT, resolved to the same user ID in Identity. Do not use a client header, body, query parameter, or the optional JWT `userId` claim as the owner.

For every enabled authenticated AV user, the first milestone returns `200 application/json`:

```json
{"accounts": [], "totalCount": 0}
```

`accounts` and `totalCount` are required and never null. There is no pagination in this contract; `totalCount` equals the length of the caller's returned account list. Responses use `Cache-Control: no-store`.

The following is a future response example only. Linking, provider integrations, persistence queries and nonempty responses are outside milestone 1:

```json
{
  "accounts": [
    {"id": 101, "platform": "YOUTUBE", "accountName": "Audience Verdict", "status": "CONNECTED"}
  ],
  "totalCount": 1
}
```

Future account summaries expose only `id` (positive integer), `platform`, `accountName`, and `status` (nonempty strings). The example values do not define exhaustive platform or status enums. Never serialize provider credentials, access/refresh tokens, secrets or internal owner fields. Future reads must constrain every query by the validated owner ID; administrators and staff receive only their own accounts through this endpoint.

## Authentication and authorization

Follow the existing `av-service` JWT and Identity policy:

1. Require a Bearer access token and verify its RS256 signature using Identity's configured trusted JWKS endpoint (`/.well-known/jwks.json`). Never follow a token-supplied key URL or accept unsigned/other-algorithm tokens.
2. Require issuer `audience-verdict`, audience containing `av-api`, a present unexpired `exp`, and a nonblank `sub`. Validate `nbf` when present. Match the existing zero-clock-skew timestamp policy.
3. Reject any token containing a `purpose` claim. Existing access tokens have no such claim; registration proofs use `purpose: otp-registration`. Do not require a newly invented access-purpose claim that existing tokens lack.
4. Resolve the subject through Identity's protected `GET /internal/users/{id}` using service authentication. Require an existing enabled account and use its current effective roles, rather than trusting token roles. Repeat this check for each request so disabling and role changes take effect immediately.
5. Any enabled authenticated AV user may read their own accounts; no additional ADMIN or VERIFIER role is required. Explicitly protect this GET route before any generic public GET matcher.
6. Fail closed if trusted keys or Identity cannot be obtained. Do not return an empty success response when authentication or account-policy verification fails.

The service will own a dedicated MySQL schema, separate from `av_identity` and `av_business`, with a dedicated least-privilege database user. Schema provisioning and migrations belong to a later implementation step; Identity remains the authority for account status and roles.

## Errors

Use the existing AV JSON error envelope (`timestamp`, `status`, `error`, `message`, `path`, `fieldErrors`). Authentication and policy checks precede request parameter validation.

| HTTP | Error | Condition |
|---|---|---|
| 400 | `BAD_REQUEST` | Authenticated request contains unsupported query parameters, including `userId` |
| 401 | `UNAUTHORIZED` | Missing/malformed/expired token, invalid signature/issuer/audience/purpose/subject, missing expiration, unknown or disabled user |
| 403 | `FORBIDDEN` | Authenticated request denied by applicable AV authorization policy |
| 503 | `IDENTITY_UNAVAILABLE` | Identity lookup unavailable; use existing message `Account service is temporarily unavailable` |
| 503 | `AUTHENTICATION_UNAVAILABLE` | Trusted signing keys unavailable and token cannot be verified |

Disabled users receive `401`, matching the existing JWT filter's enabled-account check. A successfully fetched key set that does not validate a token yields `401`; dependency failure preventing verification yields `503`. Do not disclose token contents or cryptographic failure details.

## Acceptance cases

| Scenario | Expected result | Milestone |
|---|---|---|
| Valid AV access token, enabled user, no accounts | 200; exactly `{"accounts":[],"totalCount":0}` | 1 |
| Missing or malformed Bearer header | 401 | 1 |
| Expired token or missing `exp` | 401 | 1 |
| Invalid signature, unsupported algorithm or unsigned token | 401 | 1 |
| Wrong issuer or missing/wrong audience | 401 | 1 |
| Registration proof or any `purpose` claim | 401 | 1 |
| Missing/blank subject or unknown Identity user | 401 | 1 |
| Disabled user with otherwise valid token | 401 | 1 |
| Identity unavailable | 503; no account data | 1 |
| Trusted keys unavailable, verification impossible | 503; no account data | 1 |
| Enabled user with each existing AV role | 200; own empty list | 1 |
| Valid User A token plus `?userId=<B>` | 400; no User B data | 1 |
| Valid User A token with conflicting JWT `userId` or client owner header | Subject determines owner; never return another user's data | 1 |
| User A and User B have linked accounts | Only User A's records and count for User A's token, including for staff/admin callers | Future persistence milestone |

These are implementation acceptance criteria, not claims of passing runtime tests. The machine-readable companion is [social-account.openapi.yaml](social-account.openapi.yaml).

## Public portal video catalog

`GET /api/v1/social/videos?offset=0&limit=200` is also public for visitors and logged-in users. It returns the existing array of video master records without changing fields or ordering. `GET /api/v1/social/videos/{videoId}/uploads` is also public and returns the existing linked-upload data unchanged. Video detail and management routes, including assigning and unlinking uploads, retain their existing access rules. Invalid bearer tokens still return 401; anonymous callers should omit the Authorization header.

`GET /api/v1/social/portal/videos?page=0&size=6`

Gateway: `http://localhost:8080`. Direct service: `http://localhost:8085/api/portal/videos`.
No login or Authorization header is required. The local frontend proxy can use `http://localhost:5173/api/v1/social/portal/videos`.

Parameters: zero-based `page` (default 0, maximum 1000000), `size` (default 20, 1–200), optional case-insensitive `platform` (`youtube`, `instagram`, `facebook`, `linkedin`), `topic` (`MOVIE`, `AV`) and `categoryName`. Invalid parameters return 400.

Response contains `items`, `page`, `size`, `totalElements`, `totalPages`, `hasNext`.
Each item contains all master fields returned by the admin video list: `id` (`VID-...`), `title`, `description`, `title_match_key`, `created_at`, and `updated_at`, plus `uploads` (empty when none are linked). The unfiltered catalog includes the same application videos, master field values and creation-time/ID order as `/api/v1/social/videos`. The public response retains its pagination envelope and adds display information; it is not the admin endpoint's bare array. Pagination still applies: `size=6` returns at most six masters. For the equivalent of admin `offset=0&limit=200`, use `http://localhost:5173/api/v1/social/portal/videos?page=0&size=200` through the frontend proxy and read `items`. Continue while `hasNext` is true to retrieve the full catalog. Synchronization status and assignment audits remain admin-only.

Each upload includes its local numeric `id`, `platform`, `accountName`, `contentType`, `title`, `caption`, `permalink`, `thumbnailUrl`, `publishedAt`, `topic`, `categoryName`, ordered `assets`, numeric nullable `viewCount`, `likeCount`, `commentCount`, and `metricsUpdatedAt`. Metrics use stored lifetime counters. Assets include `mediaType`, `mediaUrl`, `thumbnailUrl`, `width`, `height`, `durationSeconds`, `displayOrder`. Provider media URLs may expire; use the permalink when no playable URL is available.

Approval, account enabled state, availability, publication date and user assignments do not restrict this catalog or its linked uploads. Optional filters select masters having a matching linked upload; each matching master's full upload list is returned. Unlinked social posts are not application videos and do not appear independently. Existing administrative routes retain their access rules.

```sh
curl 'http://localhost:8080/api/v1/social/portal/videos?page=0&size=200'
```
