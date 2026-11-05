# Final implementation plan: application videos, comments, replies, and consolidated metrics

Planning deliverable only. No application code changes are included.

## Intended result

Assign one stable application video ID to a logical video and manually link its YouTube, Instagram, and Facebook uploads. Read consolidated comments, replies, current metrics, and metric history using that ID, while preserving each upload's platform identity and collection status.

Example: `VID-000123` links a YouTube upload, an Instagram Reel, and a Facebook video. Reposts on the same platform may also link to this ID. Uploads can be collected before they are assigned.

## Scope and current state

Extend the existing Java/Spring Boot/MySQL read-only worker to collect comments, replies, and statistics for every accessible video belonging to configured accounts. Support YouTube, Instagram, and Facebook Pages. Make the comment model reusable for image and text posts as well. LinkedIn remains an extension example, outside the initial rollout.

The worker already stores YouTube `statistics`, Instagram like/comment counts, and optional Facebook reactions/comments/shares in `social_content.engagement_metadata`. It has no persisted comments, reply traversal, or metric history. Reuse the existing provider HTTP client, credential resolver, account leases, page transactions, retry policies, job reporting, and Flyway migrations.

“Every video” means every discoverable, authorized video, including existing database records and newly discovered content. Existing provider discovery limits still apply. Missing permissions or unsupported metrics must be visible rather than represented as zero or complete coverage.

## 1. Verify provider capabilities

Before implementing each provider, verify operations, permissions, pagination, metric definitions, and quota against its configured API version and a representative authorized account. Update PROVIDER_CAPABILITIES.md with the results.

| Provider | Comments and replies | Statistics |
|---|---|---|
| YouTube | Use `commentThreads.list` for top-level comments and `comments.list(parentId=...)` for complete reply pagination; embedded replies are not necessarily complete | Reuse batched `videos.list` statistics. Treat watch time, reach, and other owner analytics as a separate optional Analytics API capability requiring owner authorization |
| Instagram | Validate media comments and comment replies for the existing Facebook Login integration and granted permissions | Preserve current counters; add only media insights supported for the media type, API version, and account permissions |
| Facebook Pages | Validate comments/replies on the correct post or video object; preserve the relationship between feed post ID and attached video ID | Preserve current counters; validate authorized video insights separately from post engagement |

Official starting references:

- https://developers.google.com/youtube/v3/docs/commentThreads/list
- https://developers.google.com/youtube/v3/docs/comments/list
- https://developers.google.com/youtube/v3/docs/videos
- https://developers.google.com/youtube/analytics
- https://developers.facebook.com/docs/instagram-platform/instagram-graph-api/reference/ig-media/comments/
- https://developers.facebook.com/docs/instagram-platform/instagram-graph-api/reference/ig-comment/replies/
- https://developers.facebook.com/docs/instagram-platform/instagram-graph-api/reference/ig-media/insights/
- https://developers.facebook.com/docs/graph-api/reference/object/comments/
- https://developers.facebook.com/docs/graph-api/reference/video/video_insights/

Meta operations and metrics above are validation targets, not claims of confirmed access.

## 2. Add domain contracts and storage

### Application video identity and manual mapping

- Add `application_videos`: internal primary key, unique immutable application video ID, title, optional description, creation/update timestamps, and archive state. Generate IDs centrally; do not derive them from a platform ID, title, or caption.
- Add `application_video_uploads`: application video foreign key, existing `social_content` foreign key, assignment timestamps, and assignment source. A content upload belongs to at most one application video; an application video has many uploads, including multiple uploads on the same platform.
- Preserve provider object relationships separately: a Facebook post and its underlying video can expose different comments and metrics. Resolve canonical metric/comment sources explicitly so discovering the same provider object through two paths does not double-count it.
- Add assignment audit records for link, unlink, and reassignment, including old/new application video IDs, actor, time, and optional reason. Require an explicit reassignment operation when an upload is already linked elsewhere.
- Newly discovered uploads remain unassigned until manually linked. Do not auto-match videos by title, date, or caption. Collection continues for unassigned uploads.
- Linking or moving an upload changes its reporting membership immediately; comments and metric history remain attached to the upload and are not copied or recollected. Historical reports use current membership in the first release; the audit records explain mapping changes.
- Archiving a master record preserves mappings and collected data. It does not imply deleting a provider video.

### Collection models

- Add separate comment and statistics provider interfaces, discovered through the existing registry/factory pattern. Declare support for comments, replies, public statistics, and owner insights independently. Avoid platform switches in orchestration.
- Add `SocialComment`: provider comment ID, content ID, parent provider comment ID, author ID/name when available, text, published/updated/retrieved timestamps, optional likes/reply counts, observed status, provider metadata, and fingerprint. Preserve Unicode and nullable author fields.
- Add `social_comments` with an account/platform/provider-comment unique key and indexes for content, parent, and publication time. Allow replies to persist before their parent is resolved; associate every row with its content.
- Add metric observations with content ID, provider metric name, value, unit, period, dimensions, source, provider reporting window, and observation time. Keep current values and historical observations; define a configurable snapshot cadence and idempotent observation key.
- Keep views, plays, reach, impressions, watch time, and engagement distinct. Missing values remain unknown; preserve explicit zero values. Record freshness and availability separately so retained old values are not presented as newly fetched.
- Add per-content sync tasks/progress with operation, parent/thread ID where needed, cursor, phase, next-due time, last successful completion, failure state, retry time, and reconciliation progress. Add Flyway migrations after V4.

## 3. Implement durable collection

1. Enqueue independent comments and stats tasks when a video is discovered; also seed tasks for videos already in MySQL. Deduplicate by provider video identity where multiple posts reference the same video, preserving their relationships.
2. Paginate top-level comments and replies separately. Atomically persist each page, discovered reply tasks, job counts, and continuation under the account lease. Do not advance progress if persistence fails.
3. Resume after restart from durable state. Use bounded per-account request/page budgets and rotate fairly across videos so large threads cannot starve other videos.
4. Refresh recent comments with overlap where supported; periodically reconcile all accessible threads and replies. New replies on old comments and edits require revisiting old threads. Do not use unchanged comment counts as proof that comments are unchanged.
5. Fetch stats independently and batch video IDs when supported. Update engagement metadata compatibly and persist scheduled historical observations without treating retrieval time as a content edit.
6. Reuse account fencing, provider cooldowns, safe cursors, and HTTP-outside-transaction behavior. Content progress and comment/statistics progress remain independent, with operation-level job outcomes.
7. Classify disabled comments, unsupported operations, permission denial, unavailable content, invalid cursors, and transient failures separately. Back off or suspend the affected task as appropriate without silently marking it complete or blocking other videos.
8. Never infer deletion from absence. Store explicit deletion/unavailability only when supported by a reliable provider response. Provider comment totals may differ from accessible stored comments.

## 4. Scheduling and configuration

Use the existing scheduler as a bounded dispatcher. Proposed starting defaults, adjustable to quota and account size:

- New/recent videos: comments and public stats every 15 minutes.
- Older videos: daily refresh with a fair rotation across the full catalog.
- Complete comment/reply reconciliation: target weekly, spread across runs.
- Historical metric snapshots: daily; current values can refresh more often.
- Separate budgets for content, comments, replies, and statistics; reserve capacity for content discovery.

Expose platform/account enablement, intervals, recent-video age, page budgets, snapshot cadence, and optional owner-insights credentials. Calculate sustainable coverage from catalog size, comments volume, and available quota before rollout. Record backlog age and missed freshness targets; intervals are targets, not guarantees.

## 5. Verification and operations

- WireMock: multi-page comments, replies beyond embedded previews, replies added to old threads, edited comments, optional fields, disabled comments, authorization failures, quota exhaustion, invalid cursors, and partial batch responses.
- MySQL integration: idempotent upserts, reply relationships, crash/resume, atomic page progress, stale-worker fencing, snapshot deduplication, and nullable/zero metric handling.
- Scheduler/service tests: existing-video task seeding, fair rotation, separate operation failure outcomes, cooldowns, and bounded budgets without starving content discovery.
- Regression: existing content, assets, stories, playlists, and checkpoints continue passing their current tests.
- Authorized live smoke checks per provider: compare a sample video and an old thread against accessible provider data; confirm permission and metric availability. Do not assert exact totals where provider visibility differs.
- Report per operation: successful/partial/disabled/unauthorized status, pages and records, last successful refresh, metric freshness, pending replies, backlog age, and request/quota estimates. Logs must omit credentials and comment bodies.

## 6. Manual management and consolidated reads

The current application is a background worker with no HTTP server. Add application services and a local operator CLI for the first release; these provide concrete manual assignment and read/export operations without requiring a UI or HTTP deployment. Keep services reusable for a later authenticated REST API or dashboard.

Management operations:

1. Create/list/show application video records.
2. List unassigned uploads, filtered by platform, account, date, or title.
3. Link an upload using its local content ID or platform + account + external content ID.
4. Unlink or explicitly reassign an upload, recording the change atomically.
5. List all uploads linked to an application video ID.

Read/export operations by application video ID:

- Summary: master details, linked uploads, available platform metrics, compatible totals, coverage, and freshness.
- Comments: cursor-paginated top-level comments across uploads, with optional platform/upload/date filters and chronological ordering. Use timestamp plus stable ID as a tie-breaker. Fetch replies by their platform-scoped parent identity; never combine unrelated threads.
- Every comment includes platform, account, upload ID/link, author fields when available, provider comment ID, parent ID, text, publication/update time, and retrieval time. Identical text on different platforms remains separate comments.
- Metrics: per-upload and per-platform current values plus dated history. Support JSON export; provide CSV for tabular comments and metrics with spreadsheet formula injection protection.
- Coverage: show linked/discovered uploads, last success per operation, unknown/unsupported/denied metrics, pending backfill/replies, and partial results. Missing uploads that the application has never discovered cannot be counted as known coverage.

### Aggregation rules

- Maintain a provider-to-metric catalog defining source, unit, semantics, reporting period, supported content types, and whether cross-platform summation is allowed.
- Keep likes versus reactions and views versus plays separate unless a report explicitly presents a labeled combined measure with its component definitions. Reach is not deduplicated across platforms.
- Sum only distinct upload/source observations with compatible units and periods. Never sum successive lifetime snapshots; distinguish current lifetime totals from daily-period values.
- Keep provider-reported comment totals separate from stored top-level comment counts and reply counts; they may measure different accessible populations.
- Mark totals partial when values are missing, stale, or unavailable. Include component observation times; a total is not a simultaneous provider snapshot.
- For historical charts, align reporting dates and provider timezones explicitly. Do not silently treat missing days as zero or assume decreases are collection errors.
- Mapping operations and consolidated reads must use consistent database transactions so a reassignment cannot cause an upload to appear in two masters within one result.

## 7. Required inputs and release boundaries

- Private credentials for each configured account; verified read-comment/reply and statistics permissions. Supply through existing external configuration, not source control.
- Representative video IDs per platform, including a thread with paginated replies, plus a manual grouping example spanning platforms.
- Initial metrics: available public engagement/video counters. Advanced owner insights are enabled per validated capability and credentials; unavailable insights do not block public collection.
- Initial scope: videos, Reels, and other reliably classified video content already supported by discovery. The comment schema remains reusable for image/text posts; collection for those is a later opt-in extension.
- Initial manual management/reporting: application services and local CLI/exports. Web UI, REST hosting, publishing, moderation, sentiment analysis, and automatic cross-platform matching are outside this release.
- Use configurable refresh/snapshot defaults from section 4. Retention must be configurable and reconciled with applicable provider requirements before production; do not introduce automatic destructive purges without a defined policy.

Additional acceptance tests cover multi-platform grouping, multiple uploads on one platform, unassigned discovery, duplicate-link prevention, reassignment/audit, archived masters, consolidated comment pagination/thread identity, canonical-source deduplication, partial totals, differing metric definitions, historical snapshot aggregation, and concurrent mapping/read consistency.

## Delivery order and acceptance

1. Verify provider capabilities and sample access; finalize metric catalog and quota estimates.
2. Implement application video master, upload mapping, audit, migrations, and manual CLI operations. Seed existing uploads as unassigned without changing provider IDs.
3. Implement comment/statistics contracts, durable tasks, and YouTube collection with restart recovery and snapshots as the first complete provider slice.
4. Implement consolidated reads/exports and prove grouping across multiple test uploads, including historical and partial-result semantics.
5. Add Instagram comments/replies and validated media insights.
6. Add Facebook comments/replies, post/video identity mapping, and validated video insights.
7. Run full-catalog task backfill, regression/integration checks, authorized live validation, quota tuning, and operator documentation.

Done when one application video ID groups manually assigned platform uploads; operators can correct mappings with an audit trail; comments/replies and metrics can be read/exported together by that ID; existing and new accessible videos are scheduled even while unassigned; accessible replies paginate and resume without duplicates; available stats refresh with dated history; compatible totals avoid duplicate sources and expose missing data; one failed task does not halt other videos; coverage, freshness, and access limitations are visible; and existing synchronization behavior passes regression checks.
