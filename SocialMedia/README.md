# Social Media Content Synchronization Worker

Java 21 / Spring Boot 3.5.16 background worker in `com.slokam.social`. Reads official Instagram, Facebook Page and YouTube APIs into MySQL 8. Includes a local operator HTTP API for manual video assignment and consolidated comments. Includes outbound publishing for YouTube, Facebook Pages, and Instagram with YAML-configured credentials and durable per-account jobs. See [publishing setup and API](docs/PUBLISHING_API.md). Interactive provider OAuth login flows are not included. Deployment uses the root Compose architecture. See [comment inbox and outbound reply APIs](docs/COMMENT_REPLIES_API.md), [comments and assignment endpoints](docs/COMMENTS_API.md) and [engagement/statistics collection and endpoints](docs/STATISTICS_API.md).

## Microservice deployment

This application is registered as `social-media-service` on internal port 8085.
It uses the existing Config Server, Eureka, Identity JWTs, gateway, and MySQL instance.

From the repository root:

```sh
bash scripts/setup-local.sh
mvn -pl SocialMedia,api-gateway -am clean verify
# Build other platform jars first if this is a fresh checkout:
mvn -DskipTests package
docker compose up -d --build social-media-service api-gateway
```

The setup script adds `secrets/social-media-db-password` without replacing existing
keys or secrets. The one-shot database provisioner creates only `social_media` and
its dedicated user, including on existing MySQL volumes. Flyway applies this
application's migrations; publishing files persist in `social-media-data`.

Configure provider tokens in the root `.env` using `.env.example`. Account lists
start empty so a fresh deployment makes no provider calls. Copy the account
configuration from `config/application-accounts.example.yml` into
`secrets/social-media/application.yml`, keeping only the accounts you use.
This private directory is mounted read-only and loaded as external configuration.
It can also hold additional credential properties (such as `INSTAGRAM_SECOND_TOKEN`).
Do not copy credentials into the source or central config repository.

Comment inbox/reply access additionally requires an explicit account allowlist.
The map key is the Identity token's `sub` (the Identity user ID), not email or mobile:

```yaml
social:
  replies:
    operators:
      "IDENTITY_USER_ID":
        account-ids: [1, 2] # Local social_accounts IDs
```

All operator APIs require an existing Identity `ADMIN` access token. Missing/invalid
tokens return 401; non-admin tokens return 403. Reply account access defaults to none.
JWT signature, expiration, issuer `audience-verdict`, audience `av-api`, and absence
of registration-token `purpose` are validated locally using Identity's JWKS.
Basic operator passwords are no longer accepted.

Gateway API: `http://localhost:8080/api/v1/social/...` maps to internal `/api/...`.
For example, list accounts at `/api/v1/social/statistics/accounts`. Existing routes are preserved.
The OpenAPI JSON is available at `/social/v3/api-docs` through the gateway.
The service's internal Swagger UI is `/swagger-ui.html`; it accepts bearer JWTs.
The service has no published host port.

Set `PUBLISHING_PUBLIC_BASE_URL` to your HTTPS gateway origin for Meta media fetches.
Generated URLs use `/api/v1/social/content/{opaque-id}/media`. Only GET requests to
this media route, documentation, and health endpoints allow anonymous access.
Publishing/reply requests queue real provider operations; use empty account lists
or disable workers when validating infrastructure.

For IDE runs, provide `CONFIG_PASSWORD`, `EUREKA_URL`, `DB_URL`, `DB_USER`,
`DB_PASSWORD`, and `JWT_JWK_SET_URI` for your local infrastructure. Central
configuration is in `config-repo/social-media-service.yml`. The default port is 8085.

## Verification

`mvn -pl SocialMedia,api-gateway -am verify` runs unit and MySQL Testcontainers tests.
The MySQL tests skip when Docker is unavailable. Native MySQL integration tests
require a dedicated disposable test database; see `docs/ENVIRONMENT.md`.
Security tests verify JWT authorization and account scoping without live providers.

## Scheduling and durable recovery

The scheduler starts at delay zero after context initialization, then uses a 300,000 ms fixed delay measured from the completion of the previous scheduled invocation. Account processing is sequential and bounded by page budgets. Slow requests can extend the interval; five minutes is an intended cadence, not a strict wall-clock deadline.

1. INITIAL durably stores one recent page and a conservative incremental baseline equal to its oldest persisted publication timestamp (or the request boundary for an empty page). The rest of the accessible history becomes a separately persisted BACKFILL cursor.
2. Each invocation processes an incremental window and an independent bounded historical batch. Default budgets are ten pages per operation. A page, its media assets, job counts, and pagination progress commit in one transaction. HTTP requests occur outside database transactions.
3. INCREMENTAL captures a fixed upper boundary. Its lower boundary is the last successful checkpoint minus a 120-second overlap. A window spanning multiple executions persists its original bounds and next cursor. The successful checkpoint moves to the upper boundary only when the final page commits. Failures resume from the last durable page, including after missed executions.
4. Instagram media uses provider `since`/`until` filters. YouTube uploads and Facebook's ranked feed/photo retrieval conservatively traverse provider pagination and filter locally. Ordering is not treated as proof that older pages contain no relevant changes. These operations may require more API reads than the number of new records.
5. Older metadata and engagement are refreshed in a full reconciliation window, due every 24 hours by default. A window with no lower timestamp means reconciliation. There are no reliable deletion tombstones: absence never causes deletion or an invented unavailable status.
6. Long incremental traversals refresh the newest page each invocation without advancing the incomplete window checkpoint. Instagram stories use a dedicated capability-driven refresh and a separate resumable ephemeral cursor, so historical media never delays their retrieval. Expired stories cannot be recovered.
7. A database lease serializes each account across processes. Every page transaction locks and validates its lease row, fencing stale workers after a takeover. Leases last fifteen minutes and renew before requests and at commits. A request that outlives its lease cannot commit. Old RUNNING jobs become ABANDONED when an expired lease is acquired.

Unique constraints prevent duplicate content and children. Canonical SHA-256 fingerprints exclude retrieval timestamps and JSON object key order. Missing engagement values preserve previously known engagement. Changed asset collections reconcile additions, updates, and removals atomically. Content uniqueness is `(platform, account_id, external_content_id)`.

Provider cursors are opaque and never arbitrary URLs to follow. Graph next links are reduced to permitted pagination parameters, stripping access tokens and unrelated parameters. Providers can invalidate cursors while collections change; failed cursors preserve progress. An operator may clear a failed continuation to replay that operation from its beginning; writes remain idempotent. No provider API offers a transactional snapshot, so overlapping windows and full reconciliation reduce, but cannot eliminate, provider-side omissions.

## Resilience and operations

WebClient uses explicit connection/read timeouts and an 8 MiB response limit. Resilience4j retries transport errors, HTTP 429/5xx, and documented Meta throttling codes with exponential backoff and jitter. Permanent authorization errors are not retried. `Retry-After` sets a minimum retry delay. Values exceeding the configured in-process wait threshold fail fast and persist an account cooldown for subsequent schedules.

Rate limiting is per provider per worker process. Multiple workers coordinate content writes through MySQL leases; provider quota must also be allocated across processes by setting lower per-process request limits. YouTube quota units differ from request counts; configure page budgets and polling based on the project's allocated quota. A missing token, permission failure, exhausted quota, invalid response, or database error cannot advance an incomplete checkpoint.

Logs include account ID, mode, status, duration, fetched/inserted/updated/skipped/failed counts. Error messages omit remote bodies, credentials, WebClient request URLs and nested exceptions. `failed_count` counts failed retrieval/persistence operations when no reliable per-record failure count is available. `fetched_count` can exceed writes because local filters exclude out-of-window items. Jobs retain durable page counts even when a later page fails.

Micrometer records `social.sync.jobs`, `social.sync.duration`, and `social.sync.records`. Actuator exposes health and info on the internal service port. Use an externally configured Micrometer exporter or inspect the job/checkpoint tables. Do not enable HTTP wire logging containing authentication or signed media URLs. Database rows include media URLs and provider data; secure database access accordingly.

```sql
SELECT account_id, sync_mode, status, started_at, completed_at,
       fetched_count, inserted_count, updated_count, skipped_count,
       failed_count, error_message
FROM social_sync_jobs ORDER BY started_at DESC LIMIT 50;
SELECT account_id, last_successful_sync_at, window_from, window_to,
       backfill_completed, last_status, retry_not_before
FROM social_sync_checkpoints;
```

Retention of job history and provider data is an operational policy: archive/delete old jobs with a DBA-managed scheduled task. No retention period is silently imposed.

## Verification

```sh
mvn test                  # JUnit, Mockito and WireMock; no real credentials/runtime
mvn verify                # adds MySQL Testcontainers tests when a runtime is available
```

Testcontainers is test-only and requires a compatible container runtime; deployment uses the root Compose file. `MySqlIT` explicitly skips without one. The same database tests can run against native MySQL with a dedicated test database:

```sh
export TEST_DB_URL='jdbc:mysql://127.0.0.1:3306/social_sync_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
export TEST_DB_USERNAME=your_test_user
# Supply TEST_DB_PASSWORD externally.
mvn -Dit.test=LocalMySqlIT verify
```

**The native database tests clear worker tables between tests. Always use a dedicated test database.** Tests cover startup/schema validation, pagination/backfill, checkpoints, recovery, duplicates, changed content, assets, multiple accounts, locking, time filters, ephemeral stories, playlists, retries, authorization, and cooldowns. Live provider access requires externally issued credentials and is a separate deployment validation step.

See [verification results](docs/IMPLEMENTATION_REPORT.md), [extension guide](docs/EXTENDING.md), and [architecture](docs/ARCHITECTURE.md).
