package com.slokam.social;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.slokam.social.application.*;
import com.slokam.social.core.capability.*;
import com.slokam.social.core.model.*;
import com.slokam.social.core.registry.*;
import com.slokam.social.core.spi.*;
import com.slokam.social.infrastructure.persistence.*;
import com.slokam.social.scheduler.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@SpringBootTest(
    classes = {SocialSyncApplication.class, AbstractMySqlVerification.TestProvider.class},
    properties = {
      "spring.config.location=optional:classpath:/integration-test.yml",
      "spring.aop.proxy-target-class=false",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.cloud.config.enabled=false",
      "eureka.client.enabled=false",
      "jwt.jwk-set-uri=http://localhost:8081/.well-known/jwks.json",
      "social.sync.enabled=false",
      "social.comments.enabled=false",
      "social.publishing.enabled=false",
      "social.replies.enabled=false",
      "social.platforms.instagram.enabled=false",
      "social.platforms.facebook.enabled=false",
      "social.platforms.youtube.enabled=false",
      "social.sync.incremental-batch-pages=1",
      "social.sync.backfill-batch-pages=1",
      "social.platforms.test.accounts[0].external-id=test-account",
      "social.platforms.test.accounts[0].credential-reference=DUMMY"
    })
abstract class AbstractMySqlVerification {
  @TestConfiguration
  static class TestProvider {
    @Bean
    SocialPlatformAdapter fixtureAdapter() {
      var a = mock(SocialPlatformAdapter.class);
      when(a.platformId()).thenReturn("test");
      when(a.capabilities())
          .thenReturn(
              new PlatformCapabilities(EnumSet.allOf(ContentType.class), false, false, false));
      return a;
    }
  }

  @Autowired org.springframework.test.web.servlet.MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired AccountCatalog catalog;
  @Autowired PlatformRegistry registry;
  @Autowired ContentPersistenceService persistence;
  @Autowired SyncCheckpointService checkpoints;
  @Autowired AccountLeaseService leases;
  @Autowired SocialMediaSyncService sync;
  @Autowired ApplicationContext context;
  @Autowired com.slokam.social.configuration.SocialProperties properties;
  SocialAccount account;
  SocialPlatformAdapter adapter;

  @BeforeEach
  void setup() {
    for (String table :
        List.of(
            "social_comment_replies",
            "social_publications",
            "application_content",
            "social_statistics_history",
            "social_statistics_tasks",
            "social_comment_tasks",
            "social_comments",
            "application_video_uploads",
            "application_video_assignment_audit",
            "application_videos",
            "social_media_assets",
            "social_content",
            "social_sync_jobs",
            "social_sync_checkpoints",
            "social_credentials",
            "social_account_leases",
            "social_accounts")) jdbc.update("DELETE FROM " + table);
    account = catalog.enabledAccounts().getFirst();
    adapter = registry.resolve("test");
    reset(adapter);
    when(adapter.platformId()).thenReturn("test");
    when(adapter.capabilities())
        .thenReturn(
            new PlatformCapabilities(EnumSet.allOf(ContentType.class), false, false, false));
    properties.getSync().setEnabled(true);
  }

  SocialContent content(String id, String caption) {
    var c = Fixtures.content(caption);
    return new SocialContent(
        null,
        "test",
        id,
        account.id(),
        c.authorId(),
        c.authorName(),
        c.contentType(),
        c.providerContentType(),
        c.title(),
        c.caption(),
        c.permalink(),
        c.thumbnailUrl(),
        c.publishedAt(),
        c.providerUpdatedAt(),
        c.retrievedAt(),
        c.engagementMetadata(),
        c.providerMetadata(),
        c.status(),
        c.mediaAssets());
  }

  PageResult<SocialContent> page(String id, String cursor) {
    return new PageResult<>(List.of(content(id, "caption")), cursor, cursor != null, Map.of());
  }

  ContentQuery query(SyncMode mode) {
    return new ContentQuery(
        account.id(), null, Instant.now(), 100, null, EnumSet.allOf(ContentType.class), mode);
  }

  @Test
  void authenticatedVideoHttpFlowPersistsAndReadsFromMySql() throws Exception {
    var admin = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
        .jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"));
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/videos"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/videos")
        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
            .jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/videos")
        .with(admin).contentType("application/json").content("{\"title\":\"HTTP integration video\",\"description\":\"Disposable test\"}"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
        .andReturn().getResponse().getContentAsString();
    String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asText();
    assertEquals("HTTP integration video", jdbc.queryForObject(
        "SELECT title FROM application_videos WHERE id=?", String.class, id));
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/videos/" + id).with(admin))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.title").value("HTTP integration video"));
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/videos")
        .with(admin).contentType("application/json").content("{\"title\":\"\"}"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM application_videos", Integer.class));
  }

  @Test
  void persistenceAutomaticallyGroupsVideoTitlesAndRechecksUnchangedUploads() {
    var base = content("base", "caption");
    java.util.function.BiFunction<String, String, SocialContent> video = (externalId, title) ->
        new SocialContent(null, base.platform(), externalId, base.accountId(), base.authorId(),
            base.authorName(), ContentType.VIDEO, "video", title, base.caption(), base.permalink(),
            base.thumbnailUrl(), base.publishedAt(), base.providerUpdatedAt(), base.retrievedAt(),
            base.engagementMetadata(), base.providerMetadata(), base.status(), List.of());
    var first = video.apply("first-video", "HI Hello");
    var second = video.apply("second-video", "hi     HELLO");
    assertEquals(2, persistence.persist(List.of(first, second)).inserted());
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM application_videos", Integer.class));
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM application_video_uploads", Integer.class));
    assertEquals(2, persistence.persist(List.of(first, second)).skipped());
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM application_video_assignment_audit", Integer.class));
    // Simulate an old, unchanged upload that predates automatic assignment.
    jdbc.update("DELETE FROM application_video_assignment_audit");
    jdbc.update("DELETE FROM application_video_uploads");
    assertEquals(2, persistence.persist(List.of(first, second)).skipped());
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM application_video_uploads", Integer.class));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM application_videos", Integer.class));
  }

  @Test
  void discoversProvidersAndOperatorApi() {
    assertNotNull(context.getBean(com.slokam.social.api.VideoController.class));
    for (String platform : List.of("youtube", "instagram", "facebook", "test"))
      assertNotNull(registry.resolve(platform));
    assertEquals(
        12,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class));
  }

  @Test
  void mysqlUpsertsPreserveJsonAndAssets() {
    assertEquals(1, persistence.persist(List.of(content("post", "first"))).inserted());
    assertEquals(1, persistence.persist(List.of(content("post", "first"))).skipped());
    assertEquals(1, persistence.persist(List.of(content("post", "changed"))).updated());
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM social_content", Integer.class));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM social_media_assets", Integer.class));
    assertEquals(
        "changed", jdbc.queryForObject("SELECT caption FROM social_content", String.class));
    assertEquals(
        "post",
        jdbc.queryForObject(
            "SELECT JSON_UNQUOTE(JSON_EXTRACT(provider_metadata,'$.id')) FROM social_content",
            String.class));
  }

  @Test
  void rollbackKeepsPageAndCheckpointAtomic() {
    String owner = leases.acquire(account.id());
    var window = checkpoints.window(account.id(), owner, 120);
    var job = checkpoints.start(account.id(), SyncMode.INCREMENTAL, owner);
    var records = List.of(content("valid", "first"), content("x".repeat(300), "invalid"));
    var page = new PageResult<>(records, null, false, Map.of());
    assertThrows(
        RuntimeException.class,
        () -> checkpoints.commitPage(job.id, owner, records, page, query(SyncMode.INCREMENTAL)));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM social_content", Integer.class));
    var c = checkpoints.read(account.id(), SyncMode.INCREMENTAL);
    assertNull(c.lastSuccessfulSyncAt);
    assertEquals(window.windowTo, c.windowTo);
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT inserted_count FROM social_sync_jobs WHERE id=?", Integer.class, job.id));
    leases.release(account.id(), owner);
  }

  @Test
  void leaseFencesStaleWriters() {
    String first = leases.acquire(account.id());
    assertNotNull(first);
    assertNull(leases.acquire(account.id()));
    jdbc.update(
        "UPDATE social_account_leases SET expires_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6)) WHERE account_id=?",
        account.id());
    String second = leases.acquire(account.id());
    assertNotEquals(first, second);
    assertThrows(IllegalStateException.class, () -> leases.guard(account.id(), first));
    leases.release(account.id(), first);
    assertNull(leases.acquire(account.id()));
    leases.release(account.id(), second);
    assertNotNull(leases.acquire(account.id()));
  }

  @Test
  void schedulerRunsInitialThenResumesHistoricalAndIncrementalPages() {
    when(adapter.fetchContent(any(), any()))
        .thenReturn(page("first", "historical2"), page("second", "historical3"));
    new SocialMediaSyncScheduler(sync).synchronize();
    assertEquals("historical3", checkpoints.read(account.id(), SyncMode.BACKFILL).backfillCursor);
    assertNotNull(checkpoints.read(account.id(), SyncMode.INCREMENTAL).lastSuccessfulSyncAt);
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM social_content", Integer.class));
    when(adapter.fetchContent(any(), any())).thenReturn(page("first", null), page("third", null));
    new SocialMediaSyncScheduler(sync).synchronize();
    assertTrue(checkpoints.read(account.id(), SyncMode.BACKFILL).backfillCompleted);
    assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM social_content", Integer.class));
    assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM social_media_assets", Integer.class));
  }

  @Test
  void failedProviderWindowRecoversWithoutAdvancingCheckpoint() {
    when(adapter.fetchContent(any(), any())).thenReturn(page("first", null));
    sync.synchronizeAccount(account);
    Instant baseline = checkpoints.read(account.id(), SyncMode.INCREMENTAL).lastSuccessfulSyncAt;
    when(adapter.fetchContent(any(), any()))
        .thenThrow(new IllegalStateException("simulated provider outage"));
    sync.synchronizeAccount(account);
    var failed = checkpoints.read(account.id(), SyncMode.INCREMENTAL);
    assertEquals(baseline, failed.lastSuccessfulSyncAt);
    assertNotNull(failed.windowTo);
    doReturn(page("second", null)).when(adapter).fetchContent(any(), any());
    sync.synchronizeAccount(account);
    var recovered = checkpoints.read(account.id(), SyncMode.INCREMENTAL);
    assertEquals(failed.windowTo, recovered.lastSuccessfulSyncAt);
    assertNull(recovered.windowTo);
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM social_content", Integer.class));
  }

  @Test
  void reorderedJsonIsUnchangedAndRemovedAssetsAreDeleted() {
    var c = content("asset-post", "Caption");
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var first = mapper.createObjectNode().put("a", 1).put("b", 2);
    var reversed = mapper.createObjectNode().put("b", 2).put("a", 1);
    var extra =
        new MediaAsset(
            null, null, "extra", "IMAGE", "https://example.org/extra", null, 30, 40, null, 1);
    assertEquals(
        1,
        persistence
            .persist(List.of(Fixtures.copy(c, first, List.of(c.mediaAssets().getFirst(), extra))))
            .inserted());
    assertEquals(
        1,
        persistence
            .persist(
                List.of(Fixtures.copy(c, reversed, List.of(c.mediaAssets().getFirst(), extra))))
            .skipped());
    assertEquals(
        1, persistence.persist(List.of(Fixtures.copy(c, reversed, List.of(extra)))).updated());
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM social_media_assets", Integer.class));
    assertEquals(
        "extra",
        jdbc.queryForObject("SELECT external_asset_id FROM social_media_assets", String.class));
  }

  @Test
  void repliesAreScopedIdempotentAndDurable() {
    persistence.persist(List.of(content("reply-video", "caption")));
    long contentId=jdbc.queryForObject("SELECT id FROM social_content WHERE external_content_id='reply-video'",Long.class);
    jdbc.update("UPDATE social_content SET platform='youtube' WHERE id=?",contentId);
    jdbc.update("INSERT INTO social_comments(content_id,external_comment_id,author_id,comment_text,retrieved_at) VALUES(?,'parent','viewer','Hello',UTC_TIMESTAMP(6))",contentId);
    long commentId=jdbc.queryForObject("SELECT id FROM social_comments",Long.class);
    var service=context.getBean(com.slokam.social.replies.ReplyService.class);
    var actor=new com.slokam.social.replies.ReplyAccess.Actor("alice",List.of(account.id()));
    var denied=new com.slokam.social.replies.ReplyAccess.Actor("bob",List.of());
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.thread(commentId,denied));
    assertTrue(service.inbox(denied,null,null,null,false,0,50).isEmpty());
    var first=service.enqueue(commentId,"key-one","Thanks",actor);
    assertEquals(first.get("id"),service.enqueue(commentId,"key-one","Thanks",actor).get("id"));
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.enqueue(commentId,"key-one","Different",actor));
    assertEquals(1,service.inbox(actor,"youtube",account.id(),null,true,0,50).size());
    var social=new com.slokam.social.configuration.SocialProperties();
    var platform=new com.slokam.social.configuration.SocialProperties.Platform();
    var configured=new com.slokam.social.configuration.SocialProperties.Account();configured.setExternalId(account.externalAccountId());configured.setCommentRepliesEnabled(true);
    platform.setAccounts(List.of(configured));social.getPlatforms().put("youtube",platform);
    var settings=new com.slokam.social.replies.ReplyProperties();
    var operator=new com.slokam.social.replies.ReplyProperties.Operator();operator.setAccountIds(List.of(account.id()));settings.getOperators().put("alice",operator);
    var provider=mock(com.slokam.social.replies.CommentReplyProvider.class);when(provider.supports("youtube")).thenReturn(true);when(provider.send(eq("youtube"),any(),eq("parent"),eq("Thanks"))).thenReturn("remote-reply");
    var worker=new com.slokam.social.replies.ReplyWorker(jdbc,context.getBean(org.springframework.transaction.support.TransactionTemplate.class),social,settings,List.of(provider));
    worker.run();worker.run();
    assertEquals("SENT",service.get((String)first.get("id"),actor).get("status"));
    verify(provider,times(1)).send(eq("youtube"),any(),eq("parent"),eq("Thanks"));
    assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM social_comments",Integer.class));
    assertTrue(service.inbox(actor,null,null,null,true,0,50).isEmpty());
    service.resolve(commentId,true,actor);assertNotNull(service.comment(commentId,actor).get("resolved_at"));
    service.resolve(commentId,false,actor);assertNull(service.comment(commentId,actor).get("resolved_at"));
    // A crash during a POST is held, never re-sent. Manual confirmation records the observed remote ID.
    var uncertain=service.enqueue(commentId,"key-two","Another reply",actor);String uncertainId=(String)uncertain.get("id");
    jdbc.update("UPDATE social_comment_replies SET status='SENDING',started_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 2 HOUR) WHERE id=?",uncertainId);
    worker.run();assertEquals("UNKNOWN",service.get(uncertainId,actor).get("status"));
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.retry(uncertainId,actor));
    service.confirm(uncertainId,"observed-reply",actor);assertEquals("SENT",service.get(uncertainId,actor).get("status"));
    assertEquals("alice",service.get(uncertainId,actor).get("reconciled_by"));
    verify(provider,times(1)).send(anyString(),any(),anyString(),anyString());
    var failed=service.enqueue(commentId,"key-three","Rate limited",actor);String failedId=(String)failed.get("id");
    when(provider.send(eq("youtube"),any(),eq("parent"),eq("Rate limited"))).thenThrow(new com.slokam.social.publishing.PublishFailure("RETRY","Provider HTTP 429",300));
    worker.run();assertEquals("FAILED",service.get(failedId,actor).get("status"));
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.retry(failedId,actor));
    jdbc.update("UPDATE social_comment_replies SET next_attempt_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE id=?",failedId);
    service.retry(failedId,actor);assertEquals("QUEUED",service.get(failedId,actor).get("status"));
    when(provider.send(eq("youtube"),any(),eq("parent"),eq("Rate limited"))).thenThrow(new com.slokam.social.publishing.PublishFailure("UNKNOWN","Transport failure"));
    worker.run();worker.run();assertEquals("UNKNOWN",service.get(failedId,actor).get("status"));
    verify(provider,times(2)).send(eq("youtube"),any(),eq("parent"),eq("Rate limited"));
  }

  @Test
  void applicationVideoAssignmentsAndCommentRecovery() {
    persistence.persist(List.of(content("video-a", "caption")));
    long contentId =
        jdbc.queryForObject(
            "SELECT id FROM social_content WHERE external_content_id='video-a'", Long.class);
    jdbc.update(
        "UPDATE social_content SET platform='youtube',content_type='VIDEO' WHERE id=?", contentId);
    var videos = context.getBean(com.slokam.social.api.VideoService.class);
    String first = (String) videos.create("Master one", null).get("id");
    String second = (String) videos.create("Master two", null).get("id");
    videos.assign(first, contentId, false);
    videos.assign(first, contentId, false);
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> videos.assign(second, contentId, false));
    var store = context.getBean(com.slokam.social.comments.CommentStore.class);
    String owner = leases.acquire(account.id());
    store.seed(account.id(), owner);
    var task = store.next(account.id()).orElseThrow();
    var comment =
        new com.slokam.social.comments.CommentPage.Comment(
            "parent", null, null, "Author", "hello", Instant.now(), null, 0L, 2L);
    var page = new com.slokam.social.comments.CommentPage(List.of(comment), "page-2");
    store.commit(account.id(), owner, task, page, 900);
    store.commit(account.id(), owner, task, page, 900); // replay remains idempotent
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM social_comments", Integer.class));
    assertEquals(
        "page-2",
        jdbc.queryForObject(
            "SELECT page_cursor FROM social_comment_tasks WHERE id=?", String.class, task.id()));
    assertEquals(
        2, jdbc.queryForObject("SELECT COUNT(*) FROM social_comment_tasks", Integer.class));
    assertEquals(1, videos.comments(first, 0, 20).size());
    videos.assign(second, contentId, true);
    assertTrue(videos.comments(first, 0, 20).isEmpty());
    assertEquals(1, videos.comments(second, 0, 20).size());
    assertEquals(1, videos.audit(second, 0, 20).size());
    leases.release(account.id(), owner);
    assertThrows(
        IllegalStateException.class, () -> store.commit(account.id(), owner, task, page, 900));
    videos.unlink(second, contentId);
    assertEquals(1, videos.unassigned(0, 20).size());
  }

  @Test
  void seedsCommentsForMetaImagePostsButNotStoriesOrYouTubePlaylists() {
    persistence.persist(
        List.of(
            content("image-post", "caption"),
            content("story", "caption"),
            content("playlist", "caption")));
    jdbc.update(
        "UPDATE social_content SET platform='instagram' WHERE external_content_id='image-post'");
    jdbc.update(
        "UPDATE social_content SET platform='instagram',content_type='STORY' WHERE external_content_id='story'");
    jdbc.update(
        "UPDATE social_content SET platform='youtube',content_type='UNKNOWN' WHERE external_content_id='playlist'");
    var store = context.getBean(com.slokam.social.comments.CommentStore.class);
    String owner = leases.acquire(account.id());
    try {
      store.seed(account.id(), owner);
      assertEquals(
          1, jdbc.queryForObject("SELECT COUNT(*) FROM social_comment_tasks", Integer.class));
      assertEquals("image-post", store.next(account.id()).orElseThrow().externalId());
    } finally {
      leases.release(account.id(), owner);
    }
  }
  @Test
  void statisticsResumeSnapshotDeduplicationFailuresAndVideoReads() throws Exception {
    var store = context.getBean(com.slokam.social.statistics.StatisticsStore.class);
    var provider = mock(com.slokam.social.statistics.StatisticsProvider.class);
    when(provider.operations(any(),any())).thenReturn(List.of("public"));
    persistence.persist(List.of(content("metrics-video", "caption")));
    long contentId = jdbc.queryForObject("SELECT id FROM social_content", Long.class);
    var videos = context.getBean(com.slokam.social.api.VideoService.class);
    var api = context.getBean(com.slokam.social.statistics.StatisticsController.class);
    jdbc.update("UPDATE social_content SET content_type='VIDEO' WHERE id=?", contentId);
    String video = (String) videos.create("Statistics video", null).get("id");
    videos.assign(video, contentId, false);
    String owner = leases.acquire(account.id());
    try {
      store.seed(account,owner,provider,true);
      store.seed(account,owner,provider,true);
      assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM social_statistics_tasks", Integer.class));
      var first=store.start(account.id(),owner,store.next(account).orElseThrow(),28);
      var json=new com.fasterxml.jackson.databind.ObjectMapper();
      var page=new com.slokam.social.statistics.StatisticsProvider.Page(json.readTree("{\"views\":0}"), "next");
      store.commit(account.id(),owner,first,page,3600,86400,100);
      assertThrows(IllegalStateException.class, () -> store.commit(account.id(),owner,first,page,3600,86400,100));
      assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM social_statistics_history", Integer.class));
      jdbc.update("UPDATE social_statistics_tasks SET due_at=TIMESTAMPADD(DAY,1,UTC_TIMESTAMP(6)) WHERE id<>?", first.id());
      jdbc.update("UPDATE social_statistics_tasks SET due_at=UTC_TIMESTAMP(6) WHERE id=?", first.id());
      var resumed=store.start(account.id(),owner,store.next(account).orElseThrow(),28);
      assertEquals("next", resumed.cursor()); assertEquals(first.from(),resumed.from());
      assertThrows(IllegalArgumentException.class, () -> store.commit(account.id(),owner,resumed,page,3600,86400,100));
      var last=new com.slokam.social.statistics.StatisticsProvider.Page(json.readTree("{\"shares\":2}"),null);
      store.commit(account.id(),owner,resumed,last,3600,86400,100);
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM social_statistics_history", Integer.class));
      assertEquals(2, jdbc.queryForObject("SELECT JSON_LENGTH(result,'$.pages') FROM social_statistics_tasks WHERE id=?",Integer.class, first.id()));
      jdbc.update("UPDATE social_statistics_tasks SET due_at=UTC_TIMESTAMP(6) WHERE id=?",first.id());
      var fresh=store.start(account.id(),owner,store.next(account).orElseThrow(),28);
      store.commit(account.id(),owner,fresh,last,3600,86400,100);
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM social_statistics_history",Integer.class));
      store.fail(account.id(),owner,fresh,"PERMISSION_DENIED",3600);
      assertNotNull(jdbc.queryForObject("SELECT result FROM social_statistics_tasks WHERE id=?",String.class, first.id()));
      assertEquals("PERMISSION_DENIED",api.account(account.id(),0,100).getFirst().get("status"));
      assertEquals(1,api.accountHistory(account.id(),0,100).size());
      jdbc.update("UPDATE social_statistics_tasks SET due_at=UTC_TIMESTAMP(6) WHERE content_id=?",contentId);
      var upload=store.start(account.id(),owner,store.next(account).orElseThrow(),28);
      store.commit(account.id(),owner,upload,last,3600,86400,100);
      assertEquals(1,api.video(video,0,100).size());
      assertEquals(1,api.videoHistory(video,0,100).size());
      assertInstanceOf(com.fasterxml.jackson.databind.JsonNode.class,api.upload(contentId,0,100).getFirst().get("result"));
      videos.unlink(video,contentId);
      assertTrue(api.videoHistory(video,0,100).isEmpty());
      assertEquals(1,api.uploadHistory(contentId,0,100).size());
      store.seed(account,owner,provider,false);
      assertTrue(store.next(account).isEmpty());
    } finally { leases.release(account.id(),owner); }
    assertThrows(IllegalStateException.class,()->store.seed(account,owner,provider,true));
  }

  @Test
  void publishingSavesAtomicallySnapshotsAccountsAndDoesNotRepublishSuccessfulJobs() {
    var publishing = context.getBean(com.slokam.social.publishing.PublishingProperties.class);
    var service = context.getBean(com.slokam.social.publishing.PublishingService.class);
    var original = properties.getPlatforms().get("facebook");
    var platform = new com.slokam.social.configuration.SocialProperties.Platform();
    var target = new com.slokam.social.configuration.SocialProperties.Account();
    target.setExternalId("page-test");
    target.setCredentialReference("TEST_TOKEN");
    platform.setAccounts(List.of(target));
    properties.getPlatforms().put("facebook", platform);
    publishing.setEnabled(true);
    try {
      var request = new com.slokam.social.publishing.PublishingService.CreateContent("Title", "Caption",
          com.slokam.social.publishing.PublishingService.ContentType.TEXT, false, "public",
          new com.slokam.social.moderation.PostCategory(com.slokam.social.moderation.PostCategory.Topic.AV, null, "Title"));
      String id = (String) service.create("save-once", request, null).get("id");
      assertEquals(id, service.create("save-once", request, null).get("id"));
      service.publish(id);
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM application_content", Integer.class));
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM social_publications", Integer.class));
      var publisher = mock(com.slokam.social.publishing.SocialPublisher.class);
      when(publisher.advance(any())).thenReturn(new com.slokam.social.publishing.SocialPublisher.Result(
          "PUBLISHED", null, "page_post", "https://www.facebook.com/page_post"));
      var worker = new com.slokam.social.publishing.PublishingWorker(jdbc,
          context.getBean(org.springframework.transaction.support.TransactionTemplate.class), publishing,
          publisher, mock(com.slokam.social.publishing.PublishingTokens.class));
      worker.run();
      worker.run();
      verify(publisher, times(1)).advance(any());
      String publication = jdbc.queryForObject("SELECT id FROM social_publications", String.class);
      assertEquals("PUBLISHED", jdbc.queryForObject("SELECT status FROM social_publications", String.class));
      assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.retry(publication));
      service.publish(id);
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM social_publications", Integer.class));
      jdbc.update("UPDATE social_publications SET status='UNKNOWN' WHERE id=?", publication);
      assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.retry(publication));
      jdbc.update("UPDATE social_publications SET status='FAILED',resume_status='CHECKING' WHERE id=?", publication);
      service.retry(publication);
      assertEquals("CHECKING", jdbc.queryForObject("SELECT status FROM social_publications", String.class));
      jdbc.update("UPDATE social_publications SET status='RUNNING',started_at=DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 2 HOUR) WHERE id=?", publication);
      worker.run();
      assertEquals("UNKNOWN", jdbc.queryForObject("SELECT status FROM social_publications", String.class));
      // No destinations: saving for publication rolls back the content row too.
      platform.setEnabled(false);
      assertThrows(org.springframework.web.server.ResponseStatusException.class,
          () -> service.create("no-accounts", request, null));
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM application_content", Integer.class));
      var draft = new com.slokam.social.publishing.PublishingService.CreateContent("Draft", "Caption",
          com.slokam.social.publishing.PublishingService.ContentType.TEXT, true, null,
          new com.slokam.social.moderation.PostCategory(com.slokam.social.moderation.PostCategory.Topic.AV, null, "Draft"));
      service.create("draft", draft, null);
      assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM application_content", Integer.class));
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM social_publications", Integer.class));
    } finally {
      properties.getPlatforms().put("facebook", original);
      publishing.setEnabled(false);
    }
  }

}
