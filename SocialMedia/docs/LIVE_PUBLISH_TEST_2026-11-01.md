# Live publishing test — 1 November 2026

**All three registered destinations are now PUBLISHED.** After the credential updates, the saved Facebook and YouTube jobs were retried through the application's publishing API. The previously successful Instagram Reel was verified again and preserved without posting a duplicate.

| Platform | Registered account | Result | Video |
|---|---|---|---|
| Facebook | Audience Verdict / `1371997735999298` | PUBLISHED; uploading, processing and publishing phases complete | [Sample Reel](https://www.facebook.com/reel/4550036125314794/) |
| Instagram | audienceverdict / `17841414973923772` | PUBLISHED; existing Reel reverified | [Sample Reel](https://www.instagram.com/reel/DfMmV4GD9nO/) |
| YouTube | VERDICT AUDIENCE / `UCYVPdhvod5gCkpkfM41tk0w` | PUBLISHED; processing succeeded; **unlisted** | [Sample video](https://www.youtube.com/watch?v=kPUbev5QuLI) |

The sample is an 8-second, 720×1280 H.264/AAC MP4 labeled “App Upload Test.” Its title and caption identify it as a test. YouTube was intentionally submitted as unlisted. The same saved video was used for every destination.

## Independent provider verification

All verification reads returned HTTP 200:

- Facebook video `4550036125314794`: `video_status=ready`, all phases complete, `publish_status=published`; owner Page ID matches `1371997735999298`. Provider returned canonical relative permalink `/reel/4550036125314794/`.
- Instagram media `17896617564654929`: `media_type=VIDEO`, `media_product_type=REELS`, expected caption and permalink. The existing publication was retained.
- YouTube video `kPUbev5QuLI`: `uploadStatus=processed`, `processingStatus=succeeded`, `privacyStatus=unlisted`; snippet channel ID matches `UCYVPdhvod5gCkpkfM41tk0w`.

Facebook reports publication at 17:11:11 IST. The application confirmed YouTube processing completion at 17:12:20 IST. Instagram was originally published at 16:44:24 IST. All dates are 1 November 2026.

## Test execution and local records

Application content ID: `25a9ea78-735a-4b1c-908e-94a0c47d46f3`.

The original attempt had an expired Facebook token and a YouTube grant lacking the required read scope. The updated credentials passed live account verification. Only the two `ACTION_REQUIRED` jobs were retried; no new content or duplicate Instagram post was created.

The test used an isolated app instance on port 8081 and retained MySQL database `social_live_test_b61b9a38c5e541218f8876ff398be566`. The existing app on port 8080 and its application database were left untouched. All three final application publication records are `PUBLISHED` with external IDs and links.

Media fetching used a temporary HTTPS tunnel restricted to the exact sample media path. The full MP4 checksum and HTTP 206 byte-range response were checked before retrying. After completion, the temporary app, proxy and tunnel were stopped. No source code or configured credentials were changed during this retry.

Saved artifacts (media and JSON are under the ignored data directory):

- `data/publishing/live-test-2026-11-01/sample-upload-test.mp4`
- `data/publishing/live-test-2026-11-01/results.json`
- `data/publishing/live-test-2026-11-01/provider-verification.json`
- `data/publishing/live-test-2026-11-01/credential-check.json`
- `data/publishing/live-test-2026-11-01/before-retry-results.json`
- `data/publishing/live-test-2026-11-01/media/`

The isolated database and media were retained for inspection. Any future test should preserve these successful records to avoid duplicate posts.
