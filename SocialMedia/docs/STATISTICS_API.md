# Engagement and statistics

Statistics collection runs independently of content and comments for every enabled configured account. The built-in provider supports YouTube, Instagram, Facebook Pages, and the optional LinkedIn organization adapter. Existing content and newly discovered content are seeded automatically, whether or not assigned to an application video. LinkedIn remains disabled unless explicitly configured.

## Coverage

| Platform | Default requests |
| --- | --- |
| YouTube | Full returned video/channel statistics; owner reports for daily views, engaged views, likes/dislikes, comments, shares, watch time, average duration/percentage, subscriber changes, playlist additions/removals, card activity, and monetary/ad statistics. Separate reports for demographics, traffic sources, geography, devices, operating systems, playback location, sharing services, subscription status, and per-video retention. |
| Instagram | Account followers, following and media counts; content likes/comments; candidate media insights for views, reach, saves, shares, interactions, follows, profile activity, navigation, watch time and reposts; candidate account interaction insights. |
| Facebook | Account followers/fans and candidate Page insights; post reactions, comments, shares and reaction types; candidate post insights for views, clicks and reaction breakdowns; video insights for views, unique viewers, watch time, completion, paid/organic views and retention using the attachment's video ID. |
| LinkedIn | Organization share engagement statistics for each share/UGC post and organization lifetime share, follower demographic, and page view/click statistics. Requires configured YYYYMM API version and organization admin access. |

“All possible” depends on API version, content type, permissions, app approval, account eligibility, historical availability and quota. APIs do not provide one universal endpoint listing every accessible statistic. The defaults are a configurable collection catalog, **not a claim that every metric is valid for every configured account or media type**. Meta's current reference pages could not be fetched during implementation (access/rate limiting); candidate insight names must be validated with authorized accounts. Rejected metrics stay visible and do not block successful ones. Do not treat a failed request as a zero.

YouTube Analytics requires an owner OAuth token with `yt-analytics.readonly`; monetary reports additionally require `yt-analytics-monetary.readonly` and eligible monetization. An API key only enables the Data API requests. The collector does not issue or renew tokens. Meta insights and LinkedIn analytics require the appropriate approved app permissions and account roles. Paid-ad account/campaign reporting through separate marketing APIs is outside these organic account/content collectors.

## Storage

Flyway migration `V6__statistics.sql` adds:

- `social_statistics_tasks`: one durable task per account/content/operation, current result JSON, status, freshness, pagination progress and due time. `content_id=0` means account scope.
- `social_statistics_history`: dated successful result snapshots, unique per task and configured time bucket. A later successful refresh in the same bucket replaces that bucket's observation.

Each result retains the provider response pages, metric names, nested values, dimensions, periods and reporting timestamps. YouTube column headers and row values are retained together. Missing fields remain missing; zero remains zero. `requestedFrom`/`requestedTo` describe the requested collection window: public counters and lifetime reports still have their provider-defined lifetime semantics. These dates do not convert them to period counts. Custom timeframe reports retain their provider reporting periods too.

`engagement_metadata` remains the existing JSON column on `social_content` populated during content sync. Independently collected statistics are in the new tables and metrics APIs; consumers should use these APIs for their refresh status and history. Statistics updates do not change content edit timestamps.

Results are not summed across platforms: view definitions, unique audiences, periods and overlapping Facebook post/video sources differ. A Facebook video can appear through multiple posts; source video IDs are retained in operation names so clients can identify repeated sources. History follows current application-video membership after reassignment, just like comments. History is collected from activation onward; owner report windows may contain earlier reporting dates if the provider makes them available.

## Read APIs

- `GET /api/statistics/accounts`: discover stored account IDs.
- `GET /api/accounts/{accountId}/metrics`: account-level current results/status.
- `GET /api/uploads/{contentId}/metrics`: content-level current results/status.
- `GET /api/videos/{videoId}/metrics`: current results/status for all linked uploads.
- Append `/history` to any of the three metrics paths for snapshots.

All metrics/history endpoints accept `afterId=0&limit=100` (maximum 200). Continue current results with the last `task_id`; continue history with the last `id`. Results are JSON objects, not encoded JSON strings. Current results include `enabled`, `status`, `last_attempt_at`, `last_success_at`, `due_at`, and a safe error message. A previously successful result stays readable after a failure, with its original success timestamp.

Statuses: `PENDING`, `SUCCESS`, `AUTH_REQUIRED`, `PERMISSION_DENIED`, `NOT_FOUND`, `REJECTED`, `QUOTA_EXCEEDED`, `FAILED`. `REJECTED` means a provider rejected the request; it may reflect metric/media/version or parameter incompatibility. It is not definitive evidence that the metric never exists. `SUCCESS` indicates request completion; an empty provider result does not establish a numeric zero. Disabling/removing a configured operation keeps its previous result with `enabled=false` once the account is next seeded. Disabling the entire collector or platform stops collection; stored timestamps must still be checked for freshness.

## Scheduling and configuration

Defaults in `application.yml`: enabled, five-minute scheduler interval, 60-second initial delay, 20 request tasks per account per tick, hourly target refresh, daily snapshot bucket, 28-day owner report lookback, daily failed-operation retry, maximum 100 pages per operation. HTTP retries add requests to the task budget; existing provider rate limiting still applies. Account leases fence concurrent writers; HTTP requests execute outside page transactions. Pagination progress is committed after each page, survives restart and rejects repeated cursors. Only complete operations publish current/history results. Provider pagination URLs containing tokens are stripped before persistence. Failures restart that operation's reporting window while preserving its last completed values.

Intervals are freshness targets subject to backlog, quota, failures and other sync work. Increase `requests-per-account` only after assessing provider budgets. `social.sync.enabled=false` also disables statistics. Set `social.statistics.enabled=false` globally or `statistics-enabled: false` on an individual account to opt out. Instagram/Facebook content-sync `engagement-enabled` is now true in the application configuration.

Lists under `social.statistics` can be replaced for the API version/account being used:

```yaml
social:
  statistics:
    instagram-media-metrics: [views, reach, saved, shares, total_interactions]
    instagram-account-metrics: [views, reach, accounts_engaged]
    facebook-post-metrics: [post_media_view, post_clicks]
    facebook-video-metrics: [total_video_views, total_video_avg_time_watched]
    facebook-account-metrics: [page_media_view]
    youtube-analytics-metrics: [views, likes, shares, estimatedMinutesWatched]
    youtube-reports:
      geography:
        dimensions: country
        metrics: views,estimatedMinutesWatched
        content-only: false
    # Additional Meta insight requests can supply version-specific parameters.
    # Example candidate: validate access/support for your account before relying on it.
    insight-reports:
      audience-age:
        platform: instagram
        scope: account
        metric: follower_demographics
        period: lifetime
        metric-type: total_value
        breakdown: age
        timeframe: last_30_days
```

`youtube-reports` supports named dimension/metric combinations; `content-only: true` restricts a report to per-video requests. The provider must support each combination. `insight-reports` supports account/content scope and period, metric type, breakdown, timeframe parameters. A timeframe replaces date bounds for that custom request. Multiple breakdowns can be represented by separate named reports. Add a `StatisticsProvider` bean for a new platform; exactly one provider must support each platform. No orchestration switch needs updating.

## References and verification

- [YouTube Data API video statistics](https://developers.google.com/youtube/v3/docs/videos#statistics)
- [YouTube owner report combinations and scopes](https://developers.google.com/youtube/analytics/channel_reports)
- [YouTube report query parameters](https://developers.google.com/youtube/analytics/reference/reports/query)
- [Instagram media insights reference](https://developers.facebook.com/docs/instagram-platform/instagram-graph-api/reference/ig-media/insights/)
- [Facebook video insights reference](https://developers.facebook.com/docs/graph-api/reference/video/video_insights/)
- [LinkedIn share statistics](https://learn.microsoft.com/en-us/linkedin/marketing/community-management/organizations/share-statistics)
- [LinkedIn follower statistics](https://learn.microsoft.com/en-us/linkedin/marketing/community-management/organizations/follower-statistics)
- [LinkedIn page statistics](https://learn.microsoft.com/en-us/linkedin/marketing/community-management/organizations/page-statistics)

Automated tests cover provider request construction, owner authorization requirements, structured responses/zero values, safe pagination, Facebook video IDs, failure isolation/cooldowns, MySQL migrations, restart progress, snapshot deduplication, stale writer rejection, API JSON results and video membership/history. Live provider permissions and complete metric coverage still require authorized account validation.
