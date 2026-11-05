# Save once and publish to registered accounts

Publishing is implemented for **YouTube channels, Facebook Pages, and Instagram professional accounts**. LinkedIn is excluded from outbound publishing. Existing read synchronization is independent.

## Configuration

Edit `src/main/resources/application.yml`. The new settings are:

```yaml
YOUTUBE_CLIENT_ID: "your-google-oauth-client-id"
YOUTUBE_CLIENT_SECRET: "your-google-oauth-client-secret"
YOUTUBE_REFRESH_TOKEN: "your-channel-refresh-token"
META_CLIENT_ID: "your-meta-app-id"
META_CLIENT_SECRET: "your-meta-app-secret"
# Existing token properties are also used for publishing:
YOUTUBE_ACCESS_TOKEN: "" # optional fallback when no refresh token is configured
FACEBOOK_PAGE_TOKEN: "your-page-access-token"
INSTAGRAM_PRIMARY_TOKEN: "your-instagram-authorized-access-token"

social:
  publishing:
    enabled: true
    storage-directory: ./data/publishing
    public-base-url: "https://media.example.com"
    youtube:
      client-id: "${YOUTUBE_CLIENT_ID}"
      client-secret: "${YOUTUBE_CLIENT_SECRET}"
    meta:
      client-id: "${META_CLIENT_ID}"
      client-secret: "${META_CLIENT_SECRET}"
```

These are excerpts; merge into the existing YAML sections rather than adding duplicate `social` keys. Environment variables can override the values. Keep populated configuration private.

Accounts remain registered under `social.platforms.<platform>.accounts`. Each enabled account is a publishing destination unless `publishing-enabled: false`. Disabled platforms/accounts and unsupported platforms are excluded. Existing account `credential-reference` values point to token property names. For each YouTube account, additionally configure `refresh-token-reference` to a distinct refresh-token property. One OAuth app/client can authorize several channels, but each channel needs its own grant. The publisher verifies the token owns the configured channel before starting an upload.

YouTube needs an enabled YouTube Data API project and an externally obtained OAuth grant with `https://www.googleapis.com/auth/youtube.upload` **and** `https://www.googleapis.com/auth/youtube.readonly` (used to verify the channel and processing status), or an appropriate broader approved scope. Obtain offline consent to get a refresh token. The worker refreshes access tokens automatically; a raw access token fallback requires manual replacement when it expires. An API key cannot authorize uploads. Interactive consent/callback routes are not part of this implementation. Google project verification/audit restrictions can affect upload visibility.

Facebook needs a Page access token with `pages_manage_posts`, applicable read permissions for processing checks, and access to that Page. Instagram uses the existing **Facebook Login** model: a professional account linked to a Facebook Page and an account-authorized token with `instagram_content_publish` and its required read permissions. App review/advanced access may be needed for accounts beyond app roles. Meta client ID identifies the app for account provisioning. When a Meta client secret is configured, the worker signs Graph requests with `appsecret_proof`; tokens must have been issued by that same app. The worker uses the supplied account tokens. It does not exchange or automatically renew Meta tokens. Client ID/secret alone cannot authorize publishing.

## Media storage and public access

Files are streamed into the configured directory before content and its jobs commit atomically to MySQL. Keep this directory on persistent storage; multiple app instances must share it. Default upload size is 512 MiB; change both Spring multipart limits and `max-media-bytes` together if needed. Media content must comply with each provider's actual codec, duration, dimensions, aspect ratio and size requirements; provider errors are recorded per destination. MIME validation accepts JPEG/PNG images and MP4 video. It does not transcode files or inspect their codecs.

YouTube streams the saved file directly to Google and needs no public media URL. Facebook and Instagram fetch it from:

```
GET https://media.example.com/api/content/{opaque-content-uuid}/media
```

Configure `public-base-url` to a public HTTPS reverse proxy pointing to this route. GET supports byte ranges through Spring MVC. Expose only this media route to the internet; keep write/operator APIs on the existing localhost binding or behind authenticated application access. A content UUID functions as a bearer media link: anyone with the URL can fetch the original, including drafts. Do not expose draft URLs to untrusted parties. The API follows the project's existing local operator model; it is not a multi-tenant authorization system. Media retention/deletion and orphan-file cleanup are operational policies; no files are automatically deleted after publishing.

## Create content and automatically publish

### Troubleshooting: only YouTube publishes

If Facebook and Instagram jobs are `ACTION_REQUIRED` and logs point to `PublishingMediaStore.publicUrl`, set `PUBLISHING_PUBLIC_BASE_URL` in the repository root `.env` to your public HTTPS gateway origin, such as `https://api.example.com`. An empty value or an HTTP URL fails before any Meta upload request. Localhost and Docker service names cannot serve media to Meta.

With the Compose configuration, the media URL is `https://api.example.com/api/v1/social/content/{id}/media`. The gateway rewrites this to the service's `/api/content/{id}/media`. Configure the public reverse proxy to serve this GET route without a login page or authentication challenge, and verify that downloading it from outside your local network returns the original video. Do not use the frontend hostname unless it proxies this route to the backend.

After updating `.env`, recreate the service to load the environment:

```sh
docker compose up -d --no-deps --force-recreate social-media-service
```

Provider HTTP 401/code 190 in Facebook sync logs is a separate credential failure. Replace the rejected Page token in its configured credential source; setting the public URL does not repair tokens. Recreate the service after changing environment credentials.

Inspect `GET /api/v1/social/content/{id}` through the gateway for each publication's `error_message`. Once configuration and credentials are corrected, retry only the affected publications with `POST /api/v1/social/publications/{publicationId}/retry`. Completed YouTube publications do not need another upload. Eureka replication HTTP 401 messages are a separate discovery configuration issue.

`POST /api/content` accepts multipart form data:

- `metadata`: JSON with `title`, `caption`, `type` (`TEXT`, `IMAGE`, `VIDEO`), optional `draft` (default false), and `youtubePrivacy` (`public`, `private`, `unlisted`; default public).
- `file`: required for images/videos; omitted for text.
- `Idempotency-Key` header: required, up to 128 letters/digits/`_.:-`. Reuse the same key when retrying the same save request. The first accepted payload wins for a key.

```sh
curl http://localhost:8080/api/content \
  -H 'Idempotency-Key: campaign-video-001' \
  -F 'metadata={"title":"Product launch","caption":"Our new release","type":"VIDEO","youtubePrivacy":"public"};type=application/json' \
  -F 'file=@/absolute/path/launch.mp4;type=video/mp4'
```

A successful response is HTTP 201 with the saved content ID and the per-account `publications` list. All enabled YouTube/Facebook/Instagram accounts are snapshotted into durable jobs. Missing credentials fail only that destination. Content and destination jobs commit together; no provider calls happen in the save request. No configured publishing accounts results in HTTP 400; use `draft: true` to save without accounts or while publishing is disabled.

Text example:

```sh
curl http://localhost:8080/api/content \
  -H 'Idempotency-Key: announcement-001' \
  -F 'metadata={"title":"Announcement","caption":"Hello everyone!","type":"TEXT"};type=application/json'
```

| Content | YouTube | Facebook Page | Instagram |
|---|---|---|---|
| Text | Failed: unsupported | Feed post | Failed: unsupported |
| JPEG image | Failed: unsupported | Photo post | Image post |
| PNG image | Failed: unsupported | Photo post | Failed: requires JPEG |
| MP4 video | Video upload | Page video upload | Reel |

Unsupported destinations receive a visible `FAILED` result without blocking other accounts. Carousels, Stories, scheduled publishing, transcoding and platform-specific caption overrides are not implemented.

## Drafts, results and retry

- `GET /api/content/{id}` returns content plus each destination's status, external ID, link and safe error message. Private upload-session references and credentials are never returned.
- `POST /api/content/{id}/publish` queues an existing draft. Repeated requests do not republish or add newly registered accounts to an existing publication.
- `POST /api/publications/{publicationId}/retry` returns HTTP 202 for `FAILED` or `ACTION_REQUIRED` jobs and resumes the last known stage. Other states return HTTP 409. Retrying a processing failure checks the existing upload/container; it does not silently create a new post. Fix invalid media by saving new content with a new idempotency key.

Statuses: `QUEUED` → `RUNNING` → `PROCESSING` / `CHECKING` → `PUBLISHED`, or `FAILED`, `ACTION_REQUIRED`, `UNKNOWN`. Jobs run every 15 seconds and provider processing is polled at least 30 seconds apart. Transient read failures and explicit throttling responses retry later. The 24-hour deadline moves unfinished jobs to `ACTION_REQUIRED`. An operator retry grants another 24-hour window; inspect the provider first and save new content if the underlying upload has expired.

`PUBLISHED` means the provider accepted/processed the post. YouTube visibility is governed by the requested privacy and project restrictions. It does not promise a public post when privacy is private/unlisted.

MySQL row locking with `SKIP LOCKED` permits multiple workers to claim different jobs. A lost/ambiguous mutation response is `UNKNOWN` rather than blindly retrying a POST. A worker stopped during a request is marked `UNKNOWN` after one hour. Inspect the account/provider before any manual database recovery. Known YouTube sessions can resume after transport failures by checking their acknowledged byte range first. No exactly-once guarantee exists across a provider call and a local commit.

Published IDs are stored in `social_publications`; the existing inbound synchronizer will separately import those remote posts. Automatic association to the older `application_videos` comments/statistics grouping is not included; existing assignment endpoints remain available.

## Verification

Run `mvn test` for mocked HTTP/provider and API coverage; `mvn verify` also runs database integration tests when a compatible test container runtime is available. Native MySQL integration tests use the existing dedicated `TEST_DB_*` configuration. Tests do not publish live posts or require real provider credentials.

Official references used for implementation:

- https://developers.google.com/youtube/data/guides/using_resumable_upload_protocol
- https://developers.google.com/identity/protocols/oauth2/web-server
- https://www.postman.com/meta/instagram/documentation/6yqw8pt/instagram-api (Meta's official collection)
- https://developers.facebook.com/docs/pages-api/posts/
