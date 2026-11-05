# Official provider operations and limits

Documentation checked on 8 October 2026. Meta pages were retrieved directly because the web browsing tool returned 429/access errors. Their current examples use `v26.0`. Live authorization checks need your provider-issued credentials.

| Platform | Implemented official read operations | Classification/data | Pagination and incremental behavior |
|---|---|---|---|
| Instagram with Facebook Login | IG User profile, `/{ig-user-id}/media`, `/{media-id}/children`, `/{ig-user-id}/stories` | Image, video, Reel by `media_product_type=REELS`, carousel children; stories separately; caption, author, links, optional like/comment counts | `since`/`until` for media; cursor or safe time-based continuation; story snapshots use an independent persisted cursor. Older fields refreshed by daily reconciliation |
| Facebook Pages | Page profile, `/{page-id}/feed`, `/{post-id}/attachments`, `/{page-id}/photos?type=uploaded`; Video node client available | Text, photo, video, multi-image attachments; uploaded photos; provider-reported Reel/Live types when exposed; optional reactions/comments/shares | Feed and photos have distinct cursor phases; ranked feed ordering is not used to stop pagination; local publication/update filtering, full periodic reconciliation |
| YouTube | `channels.list`, uploads `playlistItems.list`, `videos.list`, `playlists.list`, each playlist's `playlistItems.list` | Video, available statistics/duration, channel details, liveStreamingDetails; playlists stored as UNKNOWN with provider type `youtube#playlist`; nested provider metadata retained | `nextPageToken`, maximum page size 50; opaque continuation tracks uploads, playlist-list page/index and playlist-item cursor; uploads have no `publishedAfter` parameter, so local filtering |

## Instagram constraints

The official [IG User Media reference](https://developers.facebook.com/docs/instagram-platform/instagram-graph-api/reference/ig-user/media/) supports time-based pagination, limits media retrieval to 10,000 most recently created items and excludes stories. This bounds “all history” to officially accessible history. Deleted/unavailable content cannot be reconstructed.

The [Stories reference](https://developers.facebook.com/docs/instagram-platform/instagram-graph-api/reference/ig-user/stories/) limits availability to 24 hours, excludes Live Video stories and reshared stories, and requires `instagram_basic`, `pages_read_engagement`, and applicable Business Manager permissions. A five-minute poll cannot recover content that expires while the worker is down. The [Media Children reference](https://developers.facebook.com/docs/instagram-platform/instagram-graph-api/reference/ig-media/children/) describes carousel children.

Media URLs can be omitted for copyrighted/licensed audio, even for owned video. Missing URLs remain null, rather than fabricating download links. URL expiration does not delete stored content. These APIs are for authorized professional accounts, not arbitrary private/personal feeds. Publication timestamps do not reliably reveal old caption/engagement changes, hence daily full reconciliation.

## Facebook constraints

The [Page Feed reference](https://developers.facebook.com/docs/graph-api/reference/page/feed/) requires `pages_read_engagement` and `pages_read_user_content` for feed content, with qualifying Page tasks and token authorization. Public content access is a separate approved feature. Feed history is approximately 600 ranked published posts per year, and requests accept at most 100 posts. Expired posts are unavailable. Profile/attachment/engagement fields depend on permissions. The feed can contain unpublished posts; their provider fields are retained when returned, rather than claiming they are all publicly published.

[Page Photos](https://developers.facebook.com/docs/graph-api/reference/page/photos/) supports `type=uploaded`; omitting this would retrieve profile pictures by default. Page/token/task permissions apply. Photo library objects and feed posts are separate provider objects, so both may refer to the same underlying media without being duplicate posts. Attachment children are requested with a limit of 100; an unexpectedly truncated nested result fails the page rather than advancing with missing assets.

The feed docs direct readers to the [Page VideoReels edge](https://developers.facebook.com/docs/graph-api/reference/page/video_reels/), but that edge's reference explicitly states reading is unsupported. The [Page Videos edge](https://developers.facebook.com/docs/graph-api/reference/page/videos/) also states reading is unsupported in the current reference. The worker does **not** call invented GET retrieval operations for either edge. Feed-exposed video/reel attachments are supported; complete standalone Reel/video-library enumeration is not claimed. The [Video node](https://developers.facebook.com/docs/graph-api/reference/video/) exposes authorized video metadata/source by a known video ID; a client operation is available for extensions.

## YouTube constraints

Official references: [channels.list](https://developers.google.com/youtube/v3/docs/channels/list), [playlistItems.list](https://developers.google.com/youtube/v3/docs/playlistItems/list), [videos.list](https://developers.google.com/youtube/v3/docs/videos/list), [playlists.list](https://developers.google.com/youtube/v3/docs/playlists/list), [Video resource](https://developers.google.com/youtube/v3/docs/videos).

An API key reads public data. Owner-only/private resources require valid external access tokens and proper scopes. `videos.list` may omit unavailable IDs; omission is not treated as deletion. Deleted/private playlist placeholders are skipped when metadata is inaccessible. Channel and live-stream metadata are preserved; owner-only live broadcast administration is not queried with an API key.

The Video resource has no definitive public Shorts flag. Duration alone does not establish Shorts eligibility. Such clips are classified VIDEO unless a future authorized provider field reliably identifies them. Playlist listings and individual playlist resources are stored, and playlist items drive video retrieval; a separate relational membership table is not introduced. Repeated video IDs are deduplicated by the content unique key. Video resources have no general last-modified timestamp; reconciliation refreshes old details/statistics.

Provider data retention, quota and access can change. Tests simulate documented response shapes and prove local behavior; they cannot prove your app's live permissions or provider completeness.
