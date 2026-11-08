# Post categories and review

Create-post clients must ask “Movie or AV?” instead of using a preset. Send a required `category` in the existing multipart `metadata` part of `POST /api/content`:

- Movie: `{"topic":"MOVIE","movieName":"Movie title"}`
- AV: `{"topic":"AV","applicationVideoName":"Application video title"}`

The selected name is required, trimmed, and limited to 255 characters. Supplying the other topic's name is rejected. The content response includes `topic` and `category_name`. Existing idempotency keys still return the original saved payload. Existing content is not assigned an invented category by migration.

## Admin page integration

Admin JWT required (existing ADMIN security rule). Direct service paths below use `/api`; gateway paths use `/api/v1/social`.

`GET /api/admin/posts?status=PENDING&page=0&size=20` returns every synced post awaiting approval, with platform, account, permalink, content type, and `uploadSource` (`APPLICATION` when matched to an application's publication by platform, account, and external ID; otherwise `SOCIAL_MEDIA`). It includes non-video posts as well. Posts missing a review are pending, including existing imports. `status=APPROVED` lists approved posts. Pagination includes totalElements, totalPages and hasNext.

`PUT /api/admin/posts/{socialContentId}/approval` accepts the same category object shown above. The admin chooses or corrects Movie/AV and its name in this action. Approval and category update commit together, with the admin's subject and timestamp. Repeating approval can recategorize an already approved post. AV video approval links the upload to the matching application video, creating that grouping if needed; ambiguous existing names return 409. Movie approval removes previous AV upload membership.

`GET /api/portal/videos` is a public application-video catalog. Approval does not restrict catalog visibility. Optional `topic` and `categoryName` filters select application videos with matching linked uploads. Movie approval removes AV upload membership, so movie posts without application-video membership do not appear independently in this catalog. Social provider publication jobs continue to use their existing workflow.

Movie categories are stored by name in SocialMedia; this API does not resolve names to IDs in the separate AV movie catalog. The frontend form and admin page are outside this repository and must call these APIs. Apply Flyway V10 on deployment. Previously visible imported videos require approval after rollout.
