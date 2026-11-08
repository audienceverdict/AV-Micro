package com.slokam.social;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.core.exception.ProviderException;
import com.slokam.social.core.model.*;
import com.slokam.social.infrastructure.credentials.CredentialResolver;
import com.slokam.social.infrastructure.resilience.ProviderHttpClient;
import com.slokam.social.providers.facebook.*;
import com.slokam.social.providers.instagram.*;
import com.slokam.social.providers.youtube.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;

class ProviderWireMockTest {
  WireMockServer server;
  SocialProperties properties;
  ProviderHttpClient http;
  CredentialResolver credentials;

  @BeforeEach
  void setup() {
    server = new WireMockServer(0);
    server.start();
    properties = new SocialProperties();
    properties.getHttp().setRetryBaseMs(1);
    properties.getHttp().setRequestsPerMinute(1000);
    for (String id : List.of("youtube", "instagram", "facebook")) {
      var p = new SocialProperties.Platform();
      p.setBaseUrl(server.baseUrl());
      var a = new SocialProperties.Account();
      a.setExternalId("account");
      a.setCredentialReference("TOKEN");
      a.setApiKeyReference("KEY");
      a.setIncludePlaylists(false);
      p.setAccounts(List.of(a));
      properties.getPlatforms().put(id, p);
    }
    http = new ProviderHttpClient(properties);
    credentials =
        new CredentialResolver(
            new MockEnvironment()
                .withProperty("TOKEN", "secret-token")
                .withProperty("KEY", "secret-key"));
  }

  @AfterEach
  void stop() {
    server.stop();
  }

  SocialAccount account(String platform) {
    return new SocialAccount(1L, platform, "account", "Name", "TOKEN", false);
  }

  ContentQuery query(String cursor) {
    return new ContentQuery(
        1L,
        null,
        Fixtures.NOW,
        100,
        cursor,
        EnumSet.allOf(ContentType.class),
        SyncMode.INCREMENTAL);
  }

  void stub(String path, String json) {
    server.stubFor(get(urlPathEqualTo(path)).willReturn(okJson(json)));
  }

  @Test
  void youtubeUsesUploadsPaginationAndDoesNotGuessShorts() {
    stub(
        "/channels",
        "{\"items\":[{\"id\":\"account\",\"contentDetails\":{\"relatedPlaylists\":{\"uploads\":\"uploads\"}}}]}");
    stub(
        "/playlistItems",
        "{\"items\":[{\"contentDetails\":{\"videoId\":\"v1\"}}],\"nextPageToken\":\"page2\"}");
    stub(
        "/videos",
        "{\"items\":[{\"id\":\"v1\",\"snippet\":{\"title\":\"Short clip\",\"publishedAt\":\"2026-10-08T09:00:00Z\"},\"contentDetails\":{\"duration\":\"PT30S\"},\"statistics\":{\"viewCount\":\"100\"}}]}");
    var classifier = new YouTubeClassifier();
    var adapter =
        new YouTubeAdapter(
            new YouTubeApiClient(new com.slokam.social.infrastructure.credentials.YouTubeReadAccess(
                properties, credentials, org.mockito.Mockito.mock(com.slokam.social.publishing.PublishingTokens.class), http)),
            new YouTubeMapper(classifier),
            new YouTubePlaylistMapper(),
            JsonMapper.builder().build(),
            properties);
    var page = adapter.fetchContent(account("youtube"), query(null));
    assertTrue(page.hasMore());
    assertEquals(ContentType.VIDEO, page.content().getFirst().contentType());
    assertEquals(
        30, page.content().getFirst().mediaAssets().getFirst().durationSeconds().intValue());
    adapter.fetchContent(account("youtube"), query(page.nextCursor()));
    server.verify(
        getRequestedFor(urlPathEqualTo("/playlistItems"))
            .withQueryParam("pageToken", equalTo("page2")));
  }

  @Test
  void instagramCarouselFetchesAllChildren() {
    stub("/v26.0/account", "{\"id\":\"account\",\"username\":\"author\"}");
    stub(
        "/v26.0/account/media",
        "{\"data\":[{\"id\":\"p1\",\"media_type\":\"CAROUSEL_ALBUM\",\"timestamp\":\"2026-10-08T09:00:00+0000\"}]}");
    stub(
        "/v26.0/p1/children",
        "{\"data\":[{\"id\":\"c1\",\"media_type\":\"IMAGE\",\"media_url\":\"https://example.org/1\"},{\"id\":\"c2\",\"media_type\":\"VIDEO\",\"media_url\":\"https://example.org/2\"}]}");
    var adapter =
        new InstagramAdapter(
            new InstagramApiClient(http, credentials, properties),
            new InstagramMapper(new InstagramClassifier()));
    var page = adapter.fetchContent(account("instagram"), query(null));
    assertEquals(ContentType.CAROUSEL, page.content().getFirst().contentType());
    assertEquals(2, page.content().getFirst().mediaAssets().size());
    server.verify(
        getRequestedFor(urlPathEqualTo("/v26.0/account/media"))
            .withHeader("Authorization", equalTo("Bearer secret-token")));
  }

  @Test
  void facebookFeedSupportsMultiImageAttachments() {
    stub("/v26.0/account", "{\"id\":\"account\",\"name\":\"page\"}");
    stub(
        "/v26.0/account/feed",
        "{\"data\":[{\"id\":\"post\",\"message\":\"hello\",\"created_time\":\"2026-10-08T09:00:00+0000\"}]}");
    stub(
        "/v26.0/post/attachments",
        "{\"data\":[{\"type\":\"album\",\"subattachments\":{\"data\":[{\"type\":\"photo\",\"target\":{\"id\":\"1\"}},{\"type\":\"photo\",\"target\":{\"id\":\"2\"}}]}}]}");
    var adapter =
        new FacebookAdapter(
            new FacebookApiClient(http, credentials, properties),
            new FacebookMapper(new FacebookClassifier()));
    var page = adapter.fetchContent(account("facebook"), query(null));
    assertEquals(ContentType.CAROUSEL, page.content().getFirst().contentType());
    assertEquals(2, page.content().getFirst().mediaAssets().size());
  }

  @Test
  void rateLimitsRetryButAuthorizationDoesNot() {
    server.stubFor(
        get(urlPathEqualTo("/retry"))
            .inScenario("retry")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0"))
            .willSetStateTo("OK"));
    server.stubFor(
        get(urlPathEqualTo("/retry"))
            .inScenario("retry")
            .whenScenarioStateIs("OK")
            .willReturn(okJson("{\"ok\":true}")));
    assertTrue(http.get("youtube", "retry", Map.of(), null, "secret-key").path("ok").asBoolean());
    server.verify(2, getRequestedFor(urlPathEqualTo("/retry")));
    server.stubFor(
        get(urlPathEqualTo("/denied"))
            .willReturn(aResponse().withStatus(401).withBody("secret-token")));
    var e =
        assertThrows(
            ProviderException.class,
            () -> http.get("youtube", "denied", Map.of(), "secret-token", null));
    assertFalse(e.retryable());
    assertFalse(e.getMessage().contains("secret"));
    assertNull(e.getCause());
    server.verify(1, getRequestedFor(urlPathEqualTo("/denied")));
  }

  @Test
  void missingCredentialsFailBeforeNetworkCall() {
    var resolver = new CredentialResolver(new MockEnvironment());
    assertThrows(ProviderException.class, () -> resolver.required("TOKEN"));
    assertEquals(0, server.getAllServeEvents().size());
  }

  @Test
  void malformedResponseIsRejected() {
    stub("/v26.0/account", "{\"id\":\"account\"}");
    stub("/v26.0/account/media", "{\"unexpected\":[]}");
    var adapter =
        new InstagramAdapter(
            new InstagramApiClient(http, credentials, properties),
            new InstagramMapper(new InstagramClassifier()));
    assertThrows(
        IllegalArgumentException.class,
        () -> adapter.fetchContent(account("instagram"), query(null)));
  }

  @Test
  void instagramUsesTimeBoundsAndSanitizesTimePagination() {
    stub("/v26.0/account", "{\"id\":\"account\"}");
    stub(
        "/v26.0/account/media",
        "{\"data\":[],\"paging\":{\"next\":\"https://graph.facebook.com/v26.0/account/media?access_token=do-not-store&until=123&__paging_token=p2\"}}");
    var adapter =
        new InstagramAdapter(
            new InstagramApiClient(http, credentials, properties),
            new InstagramMapper(new InstagramClassifier()));
    var q =
        new ContentQuery(
            1L,
            Fixtures.NOW.minusSeconds(120),
            Fixtures.NOW,
            50,
            null,
            Set.of(ContentType.IMAGE),
            SyncMode.INCREMENTAL);
    var page = adapter.fetchContent(account("instagram"), q);
    assertTrue(page.hasMore());
    assertFalse(page.nextCursor().contains("do-not-store"));
    server.verify(
        getRequestedFor(urlPathEqualTo("/v26.0/account/media"))
            .withQueryParam(
                "since", equalTo(String.valueOf(Fixtures.NOW.minusSeconds(120).getEpochSecond())))
            .withQueryParam("until", equalTo(String.valueOf(Fixtures.NOW.getEpochSecond() + 1))));
    adapter.fetchContent(
        account("instagram"),
        new ContentQuery(
            1L, q.from(), q.to(), 50, page.nextCursor(), q.requestedTypes(), q.mode()));
    server.verify(
        getRequestedFor(urlPathEqualTo("/v26.0/account/media"))
            .withQueryParam("until", equalTo("123"))
            .withQueryParam("__paging_token", equalTo("p2"))
            .withoutQueryParam("access_token"));
  }

  @Test
  void storiesUseDedicatedRetrievalWithoutWaitingForMediaBackfill() {
    stub("/v26.0/account", "{\"id\":\"account\"}");
    stub(
        "/v26.0/account/stories",
        "{\"data\":[{\"id\":\"story\",\"media_type\":\"IMAGE\",\"timestamp\":\"2026-10-08T09:59:00Z\"}]}");
    var adapter =
        new InstagramAdapter(
            new InstagramApiClient(http, credentials, properties),
            new InstagramMapper(new InstagramClassifier()));
    var page =
        adapter.fetchContent(
            account("instagram"),
            new ContentQuery(
                1L,
                null,
                Fixtures.NOW,
                100,
                null,
                Set.of(ContentType.STORY),
                SyncMode.INCREMENTAL));
    assertEquals(ContentType.STORY, page.content().getFirst().contentType());
    assertFalse(page.hasMore());
    server.verify(0, getRequestedFor(urlPathEqualTo("/v26.0/account/media")));
  }

  @Test
  void facebookUploadedPhotosHaveTheirOwnContinuation() {
    stub("/v26.0/account", "{\"id\":\"account\"}");
    stub("/v26.0/account/feed", "{\"data\":[]}");
    stub(
        "/v26.0/account/photos",
        "{\"data\":[{\"id\":\"photo\",\"name\":\"Caption\",\"created_time\":\"2026-10-08T09:00:00Z\",\"images\":[{\"source\":\"https://example.org/photo.jpg\",\"width\":900,\"height\":600}]}]}");
    var adapter =
        new FacebookAdapter(
            new FacebookApiClient(http, credentials, properties),
            new FacebookMapper(new FacebookClassifier()));
    var first = adapter.fetchContent(account("facebook"), query(null));
    assertEquals("photos:", first.nextCursor());
    var photos = adapter.fetchContent(account("facebook"), query(first.nextCursor()));
    assertFalse(photos.hasMore());
    assertEquals(ContentType.IMAGE, photos.content().getFirst().contentType());
    assertEquals(900, photos.content().getFirst().mediaAssets().getFirst().width());
    server.verify(
        getRequestedFor(urlPathEqualTo("/v26.0/account/photos"))
            .withQueryParam("type", equalTo("uploaded")));
  }

  @Test
  void youtubePlaylistsAndTheirItemsAreTraversed() {
    properties.getPlatforms().get("youtube").getAccounts().getFirst().setIncludePlaylists(true);
    stub(
        "/channels",
        "{\"items\":[{\"contentDetails\":{\"relatedPlaylists\":{\"uploads\":\"uploads\"}}}]}");
    stub("/playlistItems", "{\"items\":[]}");
    stub(
        "/playlists",
        "{\"items\":[{\"id\":\"pl\",\"snippet\":{\"title\":\"Playlist\",\"publishedAt\":\"2026-01-01T00:00:00Z\"}}]}");
    var adapter =
        new YouTubeAdapter(
            new YouTubeApiClient(new com.slokam.social.infrastructure.credentials.YouTubeReadAccess(
                properties, credentials, org.mockito.Mockito.mock(com.slokam.social.publishing.PublishingTokens.class), http)),
            new YouTubeMapper(new YouTubeClassifier()),
            new YouTubePlaylistMapper(),
            JsonMapper.builder().build(),
            properties);
    var first = adapter.fetchContent(account("youtube"), query(null));
    assertTrue(first.hasMore());
    var second = adapter.fetchContent(account("youtube"), query(first.nextCursor()));
    assertFalse(second.hasMore());
    assertEquals("playlist:pl", second.content().getFirst().externalContentId());
    assertEquals("youtube#playlist", second.content().getFirst().providerContentType());
    server.verify(
        getRequestedFor(urlPathEqualTo("/playlistItems"))
            .withQueryParam("playlistId", equalTo("pl")));
  }

  @Test
  void excessiveRetryAfterDefersWithoutSleepingOrRepeatedRequests() {
    server.stubFor(
        get(urlPathEqualTo("/cooldown"))
            .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "600")));
    var e =
        assertThrows(
            ProviderException.class, () -> http.get("youtube", "cooldown", Map.of(), null, "key"));
    assertEquals(600, e.retryAfter().getSeconds());
    assertFalse(e.retryable());
    server.verify(1, getRequestedFor(urlPathEqualTo("/cooldown")));
  }

  @Test
  void youtubeQuota403IsReportedWithoutRetryingAsAuthentication() {
    server.stubFor(
        get(urlPathEqualTo("/quota"))
            .willReturn(
                aResponse()
                    .withStatus(403)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
 {"error":{"code":403,"errors":[{"reason":"quotaExceeded"}]}}
 """)));
    var e =
        assertThrows(
            ProviderException.class, () -> http.get("youtube", "quota", Map.of(), null, "key"));
    assertFalse(e.retryable());
    assertTrue(e.getMessage().contains("quota exhausted"));
    assertFalse(e.getMessage().contains("credential"));
    server.verify(1, getRequestedFor(urlPathEqualTo("/quota")));
  }
}
