# AV Microservices

Six independently buildable Spring Boot applications in one Maven reactor. Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.3. The original AV-backend is not modified.

| Project | Port | Responsibility |
|---|---:|---|
| api-gateway | 8080 | Public API routes, CORS, request IDs; authentication and authorization are enforced downstream |
| discovery-server | 8761 | Private Eureka registry, HTTP Basic protected |
| config-server | 8888 | Private centralized configuration, HTTP Basic protected |
| identity-service | 8081 | Existing WhatsApp/email registration, email OTP login, accounts, roles, verifier assignments, RSA JWT issuance |
| SocialMedia (`social-media-service`) | 8085 | Social content synchronization, statistics, publishing and replies; Identity JWT authentication |
| av-service | 8082 | All existing movie, theatre, show, booking, payment, review, notification and verification features |

## Run locally with Docker Compose

From this directory:

```bash
bash scripts/setup-local.sh
# Edit .env: configure the existing WhatsApp template/provider, SMTP and administrator contacts.
mvn clean verify
docker compose up --build -d
```

Gateway routes are centrally defined in `config-repo/api-gateway.yml` and loaded from Config Server. See [all application endpoints through the gateway](docs/gateway-endpoints.md) for the complete method/path reference and configuration rollout instructions.

Use `http://localhost:8080` as the frontend API base URL. API paths, request/response fields, OTP limits and error envelopes remain unchanged. Registry and configuration ports are bound to localhost for local inspection. Identity, business and MySQL have no host ports in Compose. Eureka registration may take about a minute before Gateway resolves the initial routes.

```bash
curl http://localhost:8080/api/v1/auth/otp/config
curl http://localhost:8080/api/v1/movies
curl http://localhost:8080/.well-known/jwks.json
```

Docker Compose is for local development. It creates fresh `av_identity` and `av_business` schemas with separate users and provisions `social_media` for SocialMedia; the example database passwords in deployment/mysql-init.sql are local development credentials. Use separately provisioned secrets and least-privilege database grants for deployment.

## Authentication stays the same

1. `POST /api/v1/auth/otp/registration/request`: WhatsApp OTP to a new mobile number.
2. `POST /api/v1/auth/otp/registration/verify`: return the mobile `registrationToken`.
3. `POST /api/v1/auth/otp/registration/email/request`: email challenge bound to that mobile proof.
4. `POST /api/v1/auth/otp/registration/email/verify`: return the email proof in `registrationToken`.
5. `POST /api/v1/auth/otp/register`: submit both proofs (`registrationToken`, `emailVerificationToken`) to create the account and obtain the existing response shape.
6. Returning users use `/api/v1/auth/otp/request` and `/verify` with their email. Login never sends WhatsApp.

Legacy email-OTP routes retain their original configured/disabled behavior. Profile updates, reserved administrator identities, enabled-account checks, primary roles, additional staff roles, and assignment audits are preserved. The original 30-day default access-token lifetime is retained; override JWT_EXPIRATION_MS when intentionally changing policy. Refresh tokens/logout have not been introduced because they would change the existing authentication lifecycle.

JWT signing now uses persisted RSA keys; only Identity mounts the private key. AV obtains the public key from JWKS and checks RS256, issuer, audience, expiration, and rejects registration proofs as access tokens. Account status and current roles are fetched from Identity for every bearer-authenticated business request, retaining immediate disabling and role changes. Identity outages fail closed. Existing HS256 tokens require a fresh email login at cutover; OTP registration proofs in progress should be completed before cutover or restarted.

Global roles and verifier show IDs remain together in Identity so the existing combined additional-roles endpoint updates atomically and keeps its response shape. Identity validates assigned show IDs through AV's private API. AV enforces show assignment on ticket scanning and fetches assigned shows from its own database. There are no cross-database entity relationships or business reads of the Identity schema.

Existing roles are USER, ADMIN, VERIFIER, THEATER_MANAGER and MOVIE_MAKER. New users register through the same flow. To add a new role, extend Role consistently in both services, update the staff-role allowlist and add explicit authorization rules/tests before granting any new privileges. Theater Manager and Movie Maker retain their existing limited permissions.

## Configuration and startup

Local Config Server reads `config-repo/` using the native backend. For Git-backed configuration set CONFIG_BACKEND=git, CONFIG_GIT_URI and CONFIG_GIT_LABEL; copy the config-repo files into the private repository. Environment substitutions are resolved in each client application. Config/Eureka bootstrap credentials are environment variables, not values fetched from Config Server. Config Server imports are required, so clients fail startup if configuration is unavailable.

For IDE execution, run all applications from this root directory. Supply CONFIG_PASSWORD, EUREKA_PASSWORD, INTERNAL_SERVICE_TOKEN, and service-specific DB_URL/DB_USER/DB_PASSWORD, along with provider settings. Generate keys with setup-local.sh first. Start Config Server and Eureka, then Identity and AV, then Gateway. Each service has its own main class and executable JAR. The environment-specific settings are in `config-repo/{application-name}-{profile}.yml`.

Internal calls use Eureka/load-balanced RestClient with 3-second connect and 5-second read timeouts, and a required X-Service-Token. Gateway strips this credential and does not expose /internal routes. These internal APIs carry account/contact data and must remain on a private network; use TLS for deployments across hosts. The gateway forwards bearer tokens; all authorization stays in the existing application security policies rather than duplicating route permission rules at the edge.

OpenAPI JSON is available through `/identity/v3/api-docs` and `/av/v3/api-docs`. Swagger UI remains available on each service when accessed privately. Health: `/actuator/health` on each service; only health and info are exposed by Actuator.

## Existing database migration

See [docs/migration.md](docs/migration.md) before switching any existing deployment. Compose starts empty schemas and never imports or changes the original database. Development uses Hibernate update, matching the original application. Production profiles use schema validation and require reviewed/preprovisioned schema; AV retains and runs the catalog Flyway migrations (V1–V3), including database constraints. Identity uses Hibernate schema creation in development and validation in production. Reconcile Flyway history before importing an existing database.

## Verification

`mvn verify` runs retained OTP/provider/security/catalog/layout/ticket tests plus service-boundary tests. After building, `python3 scripts/smoke-stack.py` starts all five JARs with temporary H2 databases, verifies discovery, routing, JWT and role behavior, and shuts them down; this requires no Docker and sends no provider messages. Test WhatsApp and email providers are mocked; no live delivery is attempted. Test RSA keys live under src/test/resources only and are never used by runtime configuration. See docs/verification.md for recorded results and practical limits.


From the project directory, restart one service:
docker compose restart identity-service


If you changed .env or Docker Compose configuration, recreate it to load the changes:

docker compose up -d --no-deps identity-service

If you changed Java code, rebuild and recreate:

docker compose up -d --build --no-deps identity-service


Replace identity-service with av-service, api-gateway, config-server, or discovery-server.

View its logs:
docker compose logs -f --tail=100 identity-service
## SocialMedia service

[SocialMedia](SocialMedia/README.md) is part of the root Maven reactor and Docker Compose stack.
It uses Config Server and Eureka, validates Identity administrator JWTs, and exposes
operator APIs through `/api/v1/social/**` on the gateway. It owns the `social_media`
database and persistent publishing media volume. The `social-media-db-init` job
provisions its database/user on the existing MySQL instance.

Run `bash scripts/setup-local.sh` to generate its database secret, and follow the
[service setup guide](SocialMedia/README.md) to configure provider credentials,
accounts, and reply allowlists. Its OpenAPI JSON is at `/social/v3/api-docs`.
logs


docker compose logs -f --tail=100 --timestamps
View a specific service:
docker compose logs -f --tail=100 identity-service
Replace identity-service with any of:
- api-gateway
- av-service
- social-media-service
- config-server
- discovery-server
- mysql for database logs

Audience video assignment APIs, permissions, and Swagger URLs: [guide](SocialMedia/docs/AUDIENCE_VIDEO_ASSIGNMENTS.md).
