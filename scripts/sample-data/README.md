# Social media UI sample data

The local social_media database has been populated without modifying existing records.
Sample accounts start with UI Demo and external IDs start with ui-demo-.

To load again from the repository root:

```sh
python3 scripts/sample-data/generate-social-media.py > /tmp/social-media-ui-demo.sql
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot social_media' < /tmp/social-media-ui-demo.sql
```

Requires schema migrations V1–V8. Inserts run in one transaction; duplicate records
are left unchanged. Numeric IDs 910000–910999 are reserved for fixture sync jobs
and assignment audits; this range was checked before initial loading. Use locally.

Coverage: 8 accounts across four platforms (including disabled and empty accounts),
84 posts, images/video/carousels, Unicode and long captions, missing thumbnails,
zero/high engagement, unavailable content, 504 comments, nested and resolved
conversations, 252 sent/failed/unknown reply records, 6 application videos and
13 upload assignments with audit records. Includes unassigned videos, all three
sync modes, failed/successful sync history, comment tasks, statistics tasks and
five historical snapshots per sample task. Publishing includes 30 content items
and 5 publications each in PUBLISHED, FAILED, UNKNOWN, ACTION_REQUIRED, QUEUED,
and PROCESSING states. All 17 application tables receive at least 5 sample rows.
Flyway schema history is left to Flyway.

Sample accounts are absent from provider configuration, use fake credential
references, and have expired/unowned leases. Statistics tasks are disabled.
Comment tasks and active publication stages are due approximately ten years in
the future. No queued/sending replies are inserted, preventing automatic provider
calls. Do not manually publish or retry fixtures using real social accounts.

Images use public Picsum URLs and videos a public sample MP4; previewing requires
network access. Permalinks use example.com. Publishing content has database metadata
but no local uploaded binary files. Live OAuth, upload delivery, retries and sync
require dedicated provider test accounts and are not covered by stored fixtures.

Existing operator authorization remains in effect. Operators restricted to account
IDs need access to the sample account IDs to view those accounts in the reply inbox.
The seed does not change operator permissions.

Validation: loaded twice with no duplicate sample comments (504), checked every
application table has at least five rows, found no orphan content statistics,
and confirmed zero immediately runnable sample publications.
