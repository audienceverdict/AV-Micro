# Verification

Verified on 30 October 2026.

## Build and regression suite

Command: `mvn verify` from the project root.

- All five application modules built and produced executable JARs.
- Identity: 32 tests, zero failures/errors/skips.
- AV: 72 tests, zero failures/errors/skips.
- Total: 104 passing tests.

Coverage includes WhatsApp registration, email verification bound to the mobile proof, email-only login, proof replay protection, cooldowns, failed-attempt persistence, delivery failure rollback, administrator identity restrictions, current role/account status checks, role assignment auditing, unauthorized escalation, verifier show restrictions, seat layout/booking regressions, catalog migrations, ticket QR generation, notification email formatting, JWT verification, and private API authentication.

The 17 original controller source files are preserved exactly across Identity and AV. The combined live OpenAPI documents expose all 96 original API operations.

## Full stack smoke

Command: `python3 scripts/smoke-stack.py` after `mvn verify`.

The final run started all five packaged JARs, real Config Server, real Eureka, real Gateway and separate temporary H2 databases. AV ran its catalog Flyway migrations. The test used a temporary RSA key pair and a development administrator account. The script shut down its service processes afterward.

Passed checks:

- Config Server and Eureka health.
- Identity and AV health with separate databases.
- Gateway discovery and routing to both applications.
- All 96 original API operations in combined OpenAPI documents.
- RSA JWT validation by both Identity and AV.
- Current administrator role loaded over the private Identity API.
- CORS, private API isolation, and registration-proof rejection.
- Identity validates verifier show assignments through AV's private API and Eureka.
- Revoking an administrator role takes effect in AV with the same previously issued JWT.

Service discovery converges asynchronously during startup. Before Identity is discovered, authenticated AV requests fail closed with 503; this is covered by an additional regression test. Runtime smoke logs are written under `target/smoke/<process-id>/` and ignored by Git.

## Other checks and limits

YAML syntax, shell syntax and Python syntax checked. Provider regression tests use mocks; the stack smoke sends no WhatsApp or SMTP messages. Live provider delivery requires the user's existing provider settings in `.env` and is not claimed as tested.

Docker is unavailable on the implementation host, so Docker Compose/MySQL execution was not exercised. Compose YAML was parsed, while actual application execution was verified using H2. Validate the MySQL cutover in staging using docs/migration.md before switching an existing deployment. No source database was imported or modified.
