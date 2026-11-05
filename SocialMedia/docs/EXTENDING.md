# Adding another platform

The optional, compiled LinkedIn example under `providers/linkedin` adds an API client, classifier, mapper, adapter, and component factory. Its beans register only when `social.platforms.linkedin.enabled=true`. No scheduler, synchronization service, persistence logic, or existing provider needs a LinkedIn branch.

```yaml
social:
  platforms:
    linkedin:
      enabled: true
      base-url: https://api.linkedin.com/rest
      api-version: "202609" # use an approved, currently supported YYYYMM version
      accounts:
        - external-id: "urn:li:organization:YOUR_ORGANIZATION_ID"
          name: Organization
          credential-reference: LINKEDIN_ACCESS_TOKEN
```

The example uses the official [Posts API author finder](https://learn.microsoft.com/en-us/linkedin/marketing/community-management/shares/posts-api), its version/protocol/FINDER headers, and next-link start offsets. It supports text and preserves other post shapes as UNKNOWN JSON; it does not invent playable media URLs. Media resolution can be added as another mapper/retrieval strategy after obtaining authorized access to the relevant official APIs. `r_organization_social` and an authorized organization role, or restricted approved member-read access, are external prerequisites. No issuing or authorization flow is included.

The `LinkedInExtensibilityTest` verifies the new components register through Spring DI and work with WireMock using the shared retry/timeout client. The example is disabled by default and has no claim of live LinkedIn authorization verification.

For another platform, implement `SocialPlatformAdapter`, declare capability types, map immutable `SocialContent`/`MediaAsset` records, return opaque resumable provider cursors and safe metadata, and register components with Spring. Add the platform's account configuration. The registry discovers it automatically. Classifiers/mappers contain provider-specific conditions; the synchronization engine contains none. A new taxonomy entry may extend `ContentType` without changing business workflow or database enums, since types are stored as VARCHAR. Existing UNKNOWN content retains its original provider type and JSON.

An adapter must surface partial retrieval, invalid payloads and pagination failures as errors, not successful empty pages. Use a dedicated provider retrieval strategy when an API requires multiple calls. Never follow remote pagination URLs blindly or persist access tokens from those URLs. Request only declared types, preserve missing metric semantics, and do not assert deleted status from absence.
