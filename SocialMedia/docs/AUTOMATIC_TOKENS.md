# Social media credentials and automatic renewal

YouTube content sync, comments, statistics and publishing share one in-memory OAuth token cache.
When an account has a configured refresh token, the service exchanges it for an access token,
renews it on the next request once it is within 60 seconds of expiry, and never writes access
tokens into logs or the database.
Read requests receiving an authentication failure invalidate the cache and retry once. Permission
and quota failures do not trigger renewal. Invalid/revoked refresh tokens require reconnection;
transient renewal failures remain retryable.

## YouTube: one-time offline authorization

1. Enable YouTube Data API v3 and, if collecting owner analytics, YouTube Analytics API in the
   Google Cloud project owning your OAuth client.
2. Authorize the channel owner with `access_type=offline` and the scopes needed by your features
   (read access, analytics read access, and upload access when publishing). Use `prompt=consent`
   if necessary to obtain a refresh token for an existing grant. Exchange the authorization code
   using that same OAuth client's ID and secret.
3. Supply `YOUTUBE_CLIENT_ID`, `YOUTUBE_CLIENT_SECRET`, and `YOUTUBE_REFRESH_TOKEN` through the
   existing environment/secret configuration. Do not put actual credentials in committed YAML.
4. In the mounted `secrets/social-media/application.yml`, configure the channel:

   ```yaml
   social:
     platforms:
       youtube:
         accounts:
           - external-id: YOUR_CHANNEL_ID
             name: Your channel
             credential-reference: YOUTUBE_ACCESS_TOKEN
             refresh-token-reference: YOUTUBE_REFRESH_TOKEN
             api-key-reference: YOUTUBE_API_KEY
   ```

   `YOUTUBE_ACCESS_TOKEN` can be blank when a refresh token is supplied. The existing
   `social.publishing.youtube.client-id/client-secret` settings are shared by reads and publishing;
   they default to the environment variables above even when publishing is disabled.
5. Recreate the service after environment changes (`docker compose up -d --no-deps --force-recreate
   social-media-service`). A Docker restart does not reload Compose environment values.

An external OAuth app left in Google's **Testing** publishing status generally gets refresh tokens
that expire after seven days for YouTube scopes. Configure the appropriate production publishing
status and complete verification where required. Refresh tokens may also be revoked or expire for
other reasons; automatic refresh cannot bypass a revoked grant or add permissions.

### Public reads without OAuth

If only public videos/comments/public statistics are needed, supply `YOUTUBE_API_KEY` and omit the
refresh token. Public reads prefer that API key and do not send a configured stale access token.
Private content, owner Analytics and publishing require OAuth. API keys remain subject to quota
and configured restrictions. This is not a fallback from an invalid configured refresh token:
repair/reconnect that authorization or deliberately remove its reference to select public mode.

## Facebook: long-lived Page authorization

Facebook Page tokens do not use Google's refresh-token grant. Obtain a Page token derived from a
long-lived user token, with the permissions required by the enabled Page operations. Such Page
tokens can have no scheduled expiry, but may be invalidated by revoked permissions, account or
business access changes, or Meta security actions. Missing permissions require new authorization.

From the repository root, run the one-time setup utility:

```sh
python3 scripts/setup-facebook-page-token.py --page-id YOUR_PAGE_ID --app-id YOUR_META_APP_ID
```

It privately prompts for the app secret and a **valid user token issued for that app**, exchanges
that user token for a long-lived one, then obtains the requested Page's token. It updates only
`FACEBOOK_PAGE_TOKEN` in `.env` and makes the file private (0600). Tokens are never printed or
passed as command-line arguments. The request must be made by a user with the necessary Page
access and granted permissions. An already-expired token cannot be repaired by this utility;
log in again first. The utility does not create user consent or bypass app review.

The script defaults to Graph API v26.0; set `--api-version` to the version used by your deployment.
Use `--env-file PATH` for another environment file. Keep the file untracked. Recreate the service
with the Compose command above to load the new token. For eligible business-owned assets, a Meta
system-user setup is another option; validate permissions and token eligibility for your business.

## Verification

Check `social_sync_jobs` and service logs after the next scheduled sync. Successful refresh does
not grant missing scopes. `AUTH_REQUIRED` means credentials need attention; `PERMISSION_DENIED`
means the authorization lacks access; quota/rate-limit failures need quota/backoff handling.
Initial sync runs automatically at service startup and then on the configured interval.

Provider references:
- https://developers.google.com/identity/protocols/oauth2
- https://developers.google.com/youtube/data/guides/auth/server-side-web-apps
- https://developers.google.com/youtube/data/guides/authentication
- https://developers.facebook.com/docs/facebook-login/guides/access-tokens/get-long-lived/
