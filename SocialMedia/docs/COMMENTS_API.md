# Comments synchronization and application video API

The application now runs a servlet HTTP server and the existing background worker together. Default base URL: `http://127.0.0.1:8080/api`. Configure `SERVER_PORT` and `SERVER_ADDRESS` as needed. These operator endpoints have no built-in authentication; the default binds to loopback. Remote access requires an authenticated gateway or a subsequent application authentication implementation.

Flyway V5 creates application video masters, upload assignments/audit, comments, and durable comment tasks. Existing platform content IDs remain unchanged. These collection/assignment endpoints perform no publishing or moderation operations. The separate [unified inbox and reply API](COMMENT_REPLIES_API.md) adds authenticated comment listing, conversation retrieval, outbound replies, delivery tracking, and resolution endpoints.

## Manual assignment

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/videos` | Create master: `{"title":"My video","description":"Optional"}`; returns generated immutable `VID-<UUID>` ID |
| GET | `/videos?offset=0&limit=50` | List masters |
| GET | `/videos/{videoId}` | Master details |
| GET | `/uploads/unassigned?offset=0&limit=50` | Discover local upload IDs available for assignment; optional `platform=YOUTUBE`, `FACEBOOK` or `INSTAGRAM` |
| PUT | `/videos/{videoId}/uploads/{contentId}` | Assign local upload; idempotent for the same master; 409 if already linked elsewhere |
| PUT | `/videos/{videoId}/uploads/{contentId}/reassign` | Explicitly move an upload to this master |
| DELETE | `/videos/{videoId}/uploads/{contentId}` | Unlink without deleting content or comments |
| GET | `/videos/{videoId}/uploads` | Linked uploads, platform IDs, current stored engagement metadata and retrieval times |
| GET | `/videos/{videoId}/assignment-audit?offset=0&limit=50` | Assignment history |
| GET | `/videos/{videoId}/comments?afterId=0&limit=50` | Consolidated comments and replies across linked uploads |
| GET | `/videos/{videoId}/comment-sync` | Per-upload/thread pagination, last completion, errors, due times and page counts |
| POST | `/uploads/{contentId}/comment-sync/reset` | Restart existing comment tasks from their first page; 409 while the account lease is occupied |

Limits range from 1 to 200. Comment results are ordered by local comment ID; use the last returned `id` as the next `afterId`. This is a traversal of currently stored rows, not a live event stream: edited old comments keep their ID. Poll from zero to refresh existing comments. Reassignment changes membership immediately, so restart pagination following mapping changes.

Comments retain `content_id`, platform/account/provider upload IDs, provider comment ID, parent provider comment ID, author fields when available, text, timestamps, and optional counts. Resolve parents using **content ID + provider comment ID**, never comment ID alone. Reply rows and top-level rows are returned together; a null parent means a top-level comment. Identical comments on separate uploads remain distinct.

Example workflow:

```sh
curl -X POST http://127.0.0.1:8080/api/videos \
  -H 'Content-Type: application/json' -d '{"title":"Product demo"}'
curl http://127.0.0.1:8080/api/uploads/unassigned
curl -X PUT http://127.0.0.1:8080/api/videos/VID-REPLACE/uploads/123
curl http://127.0.0.1:8080/api/videos/VID-REPLACE/comments
```

## Scheduled collection

Supported platforms: YouTube, Instagram with the existing Facebook Login credentials, and Facebook Pages. Collection covers stored YouTube video records and Instagram/Facebook posts, images, carousels and videos for configured enabled accounts. It also collects unassigned uploads. Story comments and YouTube playlist objects are excluded. Facebook comments are collected from the stored feed post object; separate underlying-video comment collections are not inferred or combined.

YouTube top-level threads and all accessible reply pages are retrieved separately. Instagram uses media comments and comment replies; unknown reply counts cause an explicit reply read rather than an invented count. Facebook uses comment and reply edges on the stored objects. Actual availability requires valid provider permissions; live Meta permissions and response compatibility require deployment validation.

Optional configuration (defaults shown):

```yaml
social:
  comments:
    enabled: true
    interval-ms: 300000
    initial-delay-ms: 30000
    pages-per-account: 10
    refresh-seconds: 900
```

`social.sync.enabled=false` also prevents scheduled comment collection. Comment tasks are seeded automatically for existing and newly discovered videos. Each page persists comments, newly discovered reply tasks, and progress atomically under the existing account lease; HTTP requests occur outside that transaction. Replays upsert existing comments. Tasks rotate by due time within the per-account budget; unfinished pages become due after one second and completed traversals after the refresh interval. Entire accessible collections are periodically revisited to discover edits and replies on older threads. Large accounts may take longer than the target interval.

Provider failures preserve cursors and expose FAILED status. Retry-After postpones the account's comment tasks and existing content checkpoints. Invalid cursors can be reset through the endpoint above, then replayed idempotently. Missing comments never imply deletion. Comment bodies and provider exception payloads are omitted from logs. The audit records store assignment changes/time; authenticated actor identity is not available in this local operator API.

This change implements comments/replies and manual assignment. Metrics returned with linked uploads are the existing provider engagement metadata; independent statistics collection and historical snapshots are described in [STATISTICS_API.md](STATISTICS_API.md). Normalized combined totals remain outside the current API.

Unassigned uploads are paginated across all platforms in ascending local ID order. The first 50 rows can all belong to Instagram. Use `?platform=YOUTUBE&offset=0&limit=50` or `?platform=FACEBOOK&offset=0&limit=50` to browse each platform independently, or continue the combined list with `offset=50`, `offset=100`, etc. Only stored, unassigned video uploads are returned; an empty platform result requires checking account synchronization and existing assignments.
