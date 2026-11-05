# Access applications through the gateway

Base URL: `http://localhost:8080` (replace with your deployed gateway URL). Append each path below to this base URL. Replace `{...}` placeholders with real IDs.

Routes are served by Config Server from `config-repo/api-gateway.yml`. Eureka resolves the three `lb://` service destinations. Bearer tokens are forwarded; each application continues to enforce its existing authorization rules. Social Media operator APIs require an Identity administrator JWT. Request bodies and query parameters are described by the OpenAPI documents below.

## OpenAPI and gateway health

| Method | Gateway path | Application |
|---|---|---|
| GET | `/identity/v3/api-docs` | Identity |
| GET | `/social/v3/api-docs` | Social Media |
| GET | `/av/v3/api-docs` | AV |
| GET | `/actuator/health` | Gateway health |

These documentation URLs return OpenAPI JSON. Service Swagger UI and downstream Actuator endpoints are not routed through the gateway. Config Server and Eureka are private infrastructure services, not public application APIs.

## Identity

### AdminRole

| Method | Gateway path |
|---|---|
| PUT | `/api/v1/admin/users/{id}/additional-roles` |
| GET | `/api/v1/admin/users/{id}/verifier-shows` |

### AdminUser

| Method | Gateway path |
|---|---|
| GET | `/api/v1/admin/users` |
| GET | `/api/v1/admin/users/{id}` |
| PATCH | `/api/v1/admin/users/{id}/role` |
| PATCH | `/api/v1/admin/users/{id}/status` |

### Auth

| Method | Gateway path |
|---|---|
| POST | `/api/v1/auth/email-otp/request` |
| POST | `/api/v1/auth/email-otp/verify` |
| POST | `/api/v1/auth/email-otp/register` |
| GET | `/api/v1/auth/me` |
| PUT | `/api/v1/auth/me` |

### Jwks

| Method | Gateway path |
|---|---|
| GET | `/.well-known/jwks.json` |

### Otp

| Method | Gateway path |
|---|---|
| GET | `/api/v1/auth/otp/config` |
| POST | `/api/v1/auth/otp/request` |
| POST | `/api/v1/auth/otp/verify` |
| POST | `/api/v1/auth/otp/register` |
| POST | `/api/v1/auth/otp/registration/request` |
| POST | `/api/v1/auth/otp/registration/verify` |
| POST | `/api/v1/auth/otp/registration/email/request` |
| POST | `/api/v1/auth/otp/registration/email/verify` |

## AV

### Booking

| Method | Gateway path |
|---|---|
| GET | `/api/v1/bookings/me` |
| POST | `/api/v1/bookings/seat-holds` |
| GET | `/api/v1/bookings/seat-holds` |
| GET | `/api/v1/bookings/admin` |
| GET | `/api/v1/bookings/{id}` |
| GET | `/api/v1/bookings/{id}/ticket-qr` |
| POST | `/api/v1/bookings` |
| POST | `/api/v1/bookings/{id}/cancel` |
| POST | `/api/v1/bookings/admin/{id}/confirm` |
| POST | `/api/v1/bookings/admin/{id}/cancel` |
| POST | `/api/v1/bookings/admin/{id}/attendance` |
| POST | `/api/v1/bookings/admin/ticket-scan` |

### Movie

| Method | Gateway path |
|---|---|
| GET | `/api/v1/movies` |
| GET | `/api/v1/movies/{id}` |
| POST | `/api/v1/movies` |
| PUT | `/api/v1/movies/{id}` |
| DELETE | `/api/v1/movies/{id}` |

### MovieCredit

| Method | Gateway path |
|---|---|
| GET | `/api/v1/movies/{movieId}/credits` |
| GET | `/api/v1/movies/{movieId}/cast` |
| GET | `/api/v1/movies/{movieId}/crew` |
| POST | `/api/v1/movies/{movieId}/credits` |
| PUT | `/api/v1/movies/{movieId}/credits/{creditId}` |
| DELETE | `/api/v1/movies/{movieId}/credits/{creditId}` |

### MovieMedia

| Method | Gateway path |
|---|---|
| GET | `/api/v1/movies/{movieId}/media` |
| POST | `/api/v1/movies/{movieId}/media` |
| PUT | `/api/v1/movies/{movieId}/media/{mediaId}` |
| PUT | `/api/v1/movies/{movieId}/media/order` |
| PUT | `/api/v1/movies/{movieId}/media/{mediaId}/primary` |
| DELETE | `/api/v1/movies/{movieId}/media/{mediaId}` |

### Notification

| Method | Gateway path |
|---|---|
| GET | `/api/v1/notifications` |
| POST | `/api/v1/notifications/admin/campaigns` |
| POST | `/api/v1/notifications/admin/shows/{showId}/reminder` |

### Payment

| Method | Gateway path |
|---|---|
| GET | `/api/v1/payments/booking/{bookingId}` |
| POST | `/api/v1/payments/booking/{bookingId}/verify` |

### People

| Method | Gateway path |
|---|---|
| GET | `/api/v1/people` |
| GET | `/api/v1/people/{personId}` |
| POST | `/api/v1/people` |
| PUT | `/api/v1/people/{personId}` |
| GET | `/api/v1/people/{personId}/social-links` |
| POST | `/api/v1/people/{personId}/social-links` |
| PUT | `/api/v1/people/{personId}/social-links/{linkId}` |
| DELETE | `/api/v1/people/{personId}/social-links/{linkId}` |
| GET | `/api/v1/people/{personId}/filmography` |
| GET | `/api/v1/people/{personId}/movies` |

### Review

| Method | Gateway path |
|---|---|
| GET | `/api/v1/reviews` |
| POST | `/api/v1/reviews` |
| PUT | `/api/v1/reviews/{id}` |
| POST | `/api/v1/reviews/admin/{id}/moderate` |

### Show

| Method | Gateway path |
|---|---|
| GET | `/api/v1/shows` |
| GET | `/api/v1/shows/{id}` |
| GET | `/api/v1/shows/{id}/availability` |
| POST | `/api/v1/shows` |
| PUT | `/api/v1/shows/{id}` |
| DELETE | `/api/v1/shows/{id}` |

### Theatre

| Method | Gateway path |
|---|---|
| GET | `/api/v1/theatres/screens/{screenId}/layout-compatibility` |
| POST | `/api/v1/theatres/screens/{screenId}/layout-compatibility/reconcile` |
| GET | `/api/v1/theatres` |
| GET | `/api/v1/theatres/{id}` |
| POST | `/api/v1/theatres` |
| PUT | `/api/v1/theatres/{id}` |
| DELETE | `/api/v1/theatres/{id}` |
| GET | `/api/v1/theatres/{id}/screens` |
| POST | `/api/v1/theatres/{id}/screens` |
| PUT | `/api/v1/theatres/screens/{screenId}` |
| DELETE | `/api/v1/theatres/screens/{screenId}` |
| GET | `/api/v1/theatres/screens/{screenId}/layout-versions` |
| GET | `/api/v1/theatres/screens/{screenId}/layout-versions/{versionId}` |
| POST | `/api/v1/theatres/screens/{screenId}/layout-versions` |
| PUT | `/api/v1/theatres/screens/{screenId}/layout-versions/{versionId}/seats` |
| POST | `/api/v1/theatres/screens/{screenId}/layout-versions/{versionId}/publish` |

### TicketScanHistory

| Method | Gateway path |
|---|---|
| GET | `/api/v1/admin/ticket-scans` |

### Verifier

| Method | Gateway path |
|---|---|
| GET | `/api/v1/verifier/shows` |

### WaitingList

| Method | Gateway path |
|---|---|
| GET | `/api/v1/waiting-list` |
| GET | `/api/v1/waiting-list/admin` |
| POST | `/api/v1/waiting-list` |
| DELETE | `/api/v1/waiting-list/{id}` |
| POST | `/api/v1/waiting-list/{id}/claim` |

## Social Media

### Video

| Method | Gateway path |
|---|---|
| POST | `/api/v1/social/videos` |
| GET | `/api/v1/social/videos` |
| GET | `/api/v1/social/videos/{videoId}` |
| GET | `/api/v1/social/uploads/unassigned` |
| GET | `/api/v1/social/videos/{videoId}/uploads` |
| PUT | `/api/v1/social/videos/{videoId}/uploads/{contentId}` |
| PUT | `/api/v1/social/videos/{videoId}/uploads/{contentId}/reassign` |
| DELETE | `/api/v1/social/videos/{videoId}/uploads/{contentId}` |
| GET | `/api/v1/social/videos/{videoId}/comments` |
| POST | `/api/v1/social/uploads/{contentId}/comment-sync/reset` |
| GET | `/api/v1/social/videos/{videoId}/comment-sync` |
| GET | `/api/v1/social/videos/{videoId}/assignment-audit` |

### Publishing

| Method | Gateway path |
|---|---|
| POST | `/api/v1/social/content` |
| GET | `/api/v1/social/content/{id}` |
| POST | `/api/v1/social/content/{id}/publish` |
| POST | `/api/v1/social/publications/{id}/retry` |
| GET | `/api/v1/social/content/{id}/media` |

### Reply

| Method | Gateway path |
|---|---|
| GET | `/api/v1/social/comments` |
| GET | `/api/v1/social/comments/{id}/thread` |
| POST | `/api/v1/social/comments/{id}/replies` |
| GET | `/api/v1/social/comment-replies/{id}` |
| POST | `/api/v1/social/comment-replies/{id}/retry` |
| PUT | `/api/v1/social/comments/{id}/resolution` |
| POST | `/api/v1/social/comment-replies/{id}/confirm` |

### Statistics

| Method | Gateway path |
|---|---|
| GET | `/api/v1/social/statistics/accounts` |
| GET | `/api/v1/social/accounts/{accountId}/metrics` |
| GET | `/api/v1/social/uploads/{contentId}/metrics` |
| GET | `/api/v1/social/videos/{videoId}/metrics` |
| GET | `/api/v1/social/accounts/{accountId}/metrics/history` |
| GET | `/api/v1/social/uploads/{contentId}/metrics/history` |
| GET | `/api/v1/social/videos/{videoId}/metrics/history` |

## Apply configuration changes

For the local native Config Server backend, rebuild the gateway to remove the old Java routes, then restart it. The Compose Config Server mounts `config-repo`; if your deployment packages configuration instead, update/redeploy that configuration first. For a Git backend, commit these route settings to the configured private config repository before restarting the gateway.

```bash
mvn -pl api-gateway clean package
docker compose up -d --build --no-deps api-gateway
```

A gateway restart reloads the routes from Config Server; live refresh is not configured. All downstream services must be running and registered in Eureka. Initial discovery can take about a minute.

Quick read-only checks:

```bash
curl http://localhost:8080/api/v1/auth/otp/config
curl http://localhost:8080/api/v1/movies
curl http://localhost:8080/.well-known/jwks.json
curl -H "Authorization: Bearer <ADMIN_TOKEN>" http://localhost:8080/api/v1/social/statistics/accounts
```

`/internal/**` endpoints remain private and have no gateway route. Legacy `/api/v1/auth/email-otp/**` endpoints retain their existing configured/disabled behavior.
