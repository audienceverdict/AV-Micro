# Audience video assignments

Assignments link an application video (`VID-...`) to an enabled Identity user. They are separate from social-upload grouping. Assignments include the application's linked uploads; public provider links remain public.

## Permissions

- `ADMIN` and `SOCIAL_MEDIA_MANAGER`: bulk assign, read all assignments, read any user's assignments, and list a video's recipients.
- `ADMIN`: also bulk unassign videos from selected users. Social media managers cannot unassign other users.
- Authenticated audience: self-assign, self-unassign, and read own assignments only.
- Grant `SOCIAL_MEDIA_MANAGER` through `PUT /api/v1/admin/users/{id}/additional-roles` with `{"roles":["SOCIAL_MEDIA_MANAGER"],"verifierShowIds":[]}`. This replaces additional roles; include any existing roles/show assignments to preserve them. Log in again to obtain a JWT containing the new role. Existing JWTs retain claims until expiry.

## Local documentation URLs

- Swagger UI: http://localhost:8085/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8085/v3/api-docs
- OpenAPI YAML: http://localhost:8085/v3/api-docs.yaml
- OpenAPI JSON through gateway: http://localhost:8080/social/v3/api-docs

Authorize using an Identity access token. Swagger's direct-service URLs use `/api`; gateway URLs use `/api/v1/social`.

## Endpoints

All requests require `Authorization: Bearer <access-token>`.

| Method | Gateway URL | Access |
| --- | --- | --- |
| POST | http://localhost:8080/api/v1/social/video-assignments | Admin, social media manager |
| POST | http://localhost:8080/api/v1/social/me/video-assignments | Any authenticated user; recipient is JWT subject |
| DELETE | http://localhost:8080/api/v1/social/video-assignments | Admin only |
| DELETE | http://localhost:8080/api/v1/social/me/video-assignments | Any authenticated user; recipient is JWT subject |
| GET | http://localhost:8080/api/v1/social/me/video-assignments | Own assignments |
| GET | http://localhost:8080/api/v1/social/video-assignments | Admin, social media manager |
| GET | http://localhost:8080/api/v1/social/users/{userId}/video-assignments | Admin, manager, or that same user |
| GET | http://localhost:8080/api/v1/social/videos/{videoId}/assigned-users | Admin, social media manager |

Bulk body: `{"videoIds":["VID-...","VID-..."],"userIds":["user-id-1","user-id-2"]}`.
Self body: `{"videoIds":["VID-..."]}`. No recipient ID is accepted as an authority; self-assignment always uses the JWT subject.

Bulk requests create every video/user pair, with at most 50 videos and 100 users. Duplicate inputs are collapsed. Success returns HTTP 200 with `created` and `alreadyAssigned` counts. Existing assignments preserve their original source, assigner and timestamp. All video and enabled recipient checks happen before writing. Writes are transactional. Concurrent retries create only one assignment per pair.

GET endpoints accept `page` (zero-based, default 0, max 1000000) and `size` (default 20, max 100). Global listing also accepts `userId`, `videoId`, `assignedBy`, and `source` (`SELF`, `ADMIN`, `SOCIAL_MEDIA_MANAGER`). Responses contain `items`, `page`, `size`, `totalElements`, `totalPages`, and `hasNext`; rows include assignment ID, video ID, user ID, assigner ID, source, UTC timestamp, title, description, and linked upload details. Users are identified by Identity user IDs. Lists with no assignments return an empty page, including unknown user/video IDs for authorized readers.

Invalid requests return 400; invalid/missing JWT returns 401; forbidden access returns 403; assignment of a missing video/recipient returns 404; unavailable Identity service returns 503 without writing assignments.

## Configuration and rollout

Migration `V9__audience_video_assignments.sql` runs through Flyway on SocialMedia startup. Identity and SocialMedia both need the updated application builds. Config Server supplies `internal.identity-url` (default `http://identity-service`, resolved through Eureka) and `internal.service-token` from `INTERNAL_SERVICE_TOKEN`. The token must match Identity's internal-service token. The shared portal video feed is unchanged. Audience login should call `/me/video-assignments` for the personalized assigned-video view.

## Unassigning one or multiple videos

Use `DELETE /api/v1/social/me/video-assignments` with `{"videoIds":["VID-..."]}` to remove your own assignments, including assignments created by admins or managers. The recipient always comes from the JWT subject.

Admins use `DELETE /api/v1/social/video-assignments` with `{"videoIds":["VID-..."],"userIds":["user-id-1","user-id-2"]}` to remove each selected video/user pair. Both endpoints support up to 50 video IDs; admin requests support up to 100 user IDs. Include a Bearer token and `Content-Type: application/json`.

Success returns HTTP 200, for example `{"removed":1,"alreadyUnassigned":0}`. Duplicate IDs count once. Missing pairs (including unknown video/user IDs) are counted as already unassigned, making retries safe. Removal is transactional and does not call Identity, so disabled or deleted recipients can be cleaned up. Videos and linked uploads remain intact. No database migration is required.
