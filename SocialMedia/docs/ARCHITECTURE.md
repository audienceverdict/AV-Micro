# Architecture

```mermaid
flowchart TD
    Scheduler[SocialMediaSyncScheduler<br/>startup + five-minute fixed delay] --> Service[SocialMediaSyncService]
    Service --> Catalog[Configured enabled account catalog]
    Service --> Lease[MySQL account lease + stale-writer fencing]
    Service --> Registry[PlatformRegistry<br/>automatic Spring discovery]
    Registry --> Instagram[InstagramAdapter]
    Registry --> Facebook[FacebookAdapter]
    Registry --> YouTube[YouTubeAdapter]
    Instagram --> MetaHttp[Official Graph APIs]
    Facebook --> MetaHttp
    YouTube --> YouTubeHttp[Official YouTube Data API]
    MetaHttp --> Response[Normalized SocialContent + composite MediaAsset records]
    YouTubeHttp --> Response
    Response --> Normalizer[ContentNormalizer]
    Normalizer --> PageTx[Page transaction]
    PageTx --> Upsert[ContentPersistenceService<br/>fingerprint + child reconciliation]
    PageTx --> Progress[SyncCheckpointService<br/>incremental / backfill / ephemeral state]
    Upsert --> MySQL[(MySQL 8 / Flyway / JSON / UTC)]
    Progress --> MySQL
    Service --> Jobs[Durable jobs + Micrometer + logs]
```

Adapters encapsulate provider operations. Classifiers and mappers are replaceable strategies; playlist retrieval uses a specialized mapper. Component factories group each platform's adapter/classifier/mapper. The registry discovers adapters through DI and rejects duplicate platform IDs. `AbstractGraphAdapter` supplies a template workflow with provider hooks. `SocialContent.mediaAssets` represents a composite content item. Capability declarations drive requested types and ephemeral story refresh without platform switches in the synchronization service.

Transactions never span HTTP calls. Account-level fencing protects concurrent writes. MySQL unique constraints are the final duplicate guard. Separate checkpoint rows distinguish INCREMENTAL and BACKFILL; INITIAL creates both safely. Additional fields retain unfinished bounds, reconciliation schedule, ephemeral cursors and provider cooldowns. Job rows retain counts and duration per bounded operation.
