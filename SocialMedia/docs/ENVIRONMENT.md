# External account and credential configuration

The worker does not obtain, generate, refresh or exchange provider credentials. Supply existing provider-issued credentials with the required authorization. Interactive login, callbacks and OAuth client dependencies are absent.

| Setting | Purpose |
|---|---|
| `DB_URL` | MySQL JDBC URL; default database `social_media`, UTC session |
| `DB_USERNAME`, `DB_PASSWORD` | Required database credentials |
| `META_API_VERSION` | Explicit supported Graph API version; default `v26.0` |
| `SYNC_ENABLED` | Enable automatic execution; default `true` |
| `social.platforms.<platform>.accounts` | List of account IDs, names, enable flags and credential reference names |
| `credential-reference` | Name of an environment/Spring property holding the access token, not its value |
| `api-key-reference` | YouTube API key reference name; optional when a valid access token is supplied |
| `stories-enabled` | Opt into authorized Instagram story retrieval |
| `engagement-enabled` | Opt into Meta engagement fields; insufficient permission fails clearly |
| `include-photos` | Retrieve Facebook uploaded photo library in addition to feed; default true |
| `include-playlists` | Retrieve YouTube playlists/items after uploads; default true |

For local runs, `src/main/resources/application.yml` includes blank top-level token/key properties and commented account examples. Fill in the properties and configure the corresponding account IDs; references point to those property names. Set database credentials under `spring.datasource`. Run normally from the IDE or with `mvn spring-boot:run`; no additional config argument is required. Do not commit populated credentials.

Alternatively, use a secrets manager to inject environment variables, Spring ConfigTree properties or another externally managed `PropertySource`. `CredentialResolver` resolves reference names through Spring's `Environment` on each request. No tokens are saved to MySQL. The `social_credentials` table stores reference names and optional known expiration times. Expired recorded credentials fail the account job; actual provider expiration/permissions are also checked by API responses. Updating a reference's value outside the process requires the environment/property source to expose the updated value; plain OS environment variables require a process restart.

For an account used with only a YouTube API key, configure `credential-reference: YOUTUBE_ACCESS_TOKEN` and leave that property unset; supply `api-key-reference: YOUTUBE_API_KEY`. The key supports public operations, not owner-only/private data. To read owner-authorized content, supply an externally issued access token with appropriate YouTube read scopes. No token refresh is attempted.

For Instagram, this implementation uses **Instagram API with Facebook Login**, `graph.facebook.com`, an Instagram professional account ID and an existing authorized Facebook User token. It does not use Basic Display or automatically switch to Instagram Login. Media requires `instagram_basic` and applicable Page permissions. Stories have additional access constraints listed in the capability matrix. For Facebook, use a Page token for the configured Page and the required read permissions. Token issuing, account linking, app review and business authorization are external prerequisites.

Account IDs/configuration are authoritative: only configured, enabled accounts on enabled platforms are scheduled. The worker upserts account/reference rows automatically. Removing configuration stops scheduling but retains stored content. `social_accounts.enabled` mirrors configuration on each catalog load. Do not rely on editing that flag alone while configuration still enables the account. Repeat an account entry under the same platform only once; account uniqueness is enforced in MySQL.

Limits/timeouts are under `social.http`; scheduling, overlap, page budgets and reconciliation interval are under `social.sync` in `application.yml`. Provider origins and Meta versions are configurable. Use HTTPS provider origins in deployment. Local WireMock tests use HTTP. YouTube caps API page size at 50; Meta caps it at 100. Increase budgets only within your provider quota.

An external YAML file is loaded using `--spring.config.additional-location=file:/absolute/path/application-accounts.yml`. `.env.example` lists variable names only; Spring does not automatically load `.env` files. Never commit a populated `.env` or private account configuration.
