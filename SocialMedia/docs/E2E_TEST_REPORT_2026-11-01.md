# SocialMedia verification — 1 November 2026

Command: `mvn -pl SocialMedia,api-gateway -am verify`

Result: **BUILD SUCCESS** (completed at 19:26 IST).

| Suite | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: |
| SocialMedia unit, controller, security and provider tests | 73 | 0 | 0 |
| MySQL 8.0.40 Testcontainers integration tests | 13 | 0 | 0 |
| Gateway SocialMedia route test | 1 | 0 | 0 |
| Alternative native MySQL suite | 0 | 0 | 13 |

The native database suite requires TEST_DB_URL and was intentionally skipped. The equivalent shared integration scenarios ran against disposable MySQL via Testcontainers.

## Coverage

- Provider parsing, HTTP retry/error behavior via WireMock, platform registration and proxy logging.
- Identity JWT validation, ADMIN authorization, reply account access and public media exceptions.
- Real MySQL migrations/schema validation, JSON/assets persistence, transaction rollback, lease fencing, pagination/backfill and failure recovery.
- Video assignments, comments, scoped/idempotent replies, statistics pagination/history and durable publishing jobs.
- Gateway path rewriting, query and authorization forwarding, and documentation routing against a test backend.
- Added a full Spring MockMvc request flow through security, the video controller/service and real MySQL: anonymous 401, USER 403, ADMIN creation 201, database persistence, retrieval 200 and invalid request 400 without an extra database row.

## Test correction

The initial MySQL run failed during application startup because its isolated configuration omitted `spring.aop.proxy-target-class=false`. Production application.yml already specifies this setting. Added it to the integration test configuration; production code remains unchanged. The final complete verification passed.

## Scope limits

Provider calls use fixtures/mocks or WireMock. MockMvc does not start an external HTTP listener, and the gateway route test uses a test backend. This run does not validate a deployed gateway-to-service stack or live Instagram/Facebook/YouTube credentials, synchronization, publishing or replies. No real social posts or replies were sent. Docker Desktop was started to run the disposable MySQL tests.
