package com.slokam.social.publishing;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.slokam.social.configuration.SocialProperties;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class SocialPublisherTest {
  @RegisterExtension static WireMockExtension server = WireMockExtension.newInstance()
      .options(com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig().dynamicPort()).build();
  @TempDir Path directory;
  SocialProperties social;
  PublishingProperties properties;
  PublishingTokens tokens;
  PublishingMediaStore media;
  PublishingHttp http;
  SocialPublisher publisher;
  String contentId;

  @BeforeEach void setup() throws Exception {
    social = new SocialProperties();
    properties = new PublishingProperties();
    properties.setStorageDirectory(directory);
    properties.setPublicBaseUrl("https://media.example.com");
    properties.getYoutube().setUploadUrl(server.baseUrl() + "/upload/youtube/v3/videos");
    tokens = mock(PublishingTokens.class);
    when(tokens.token(anyString(), any())).thenReturn("secret-token");
    media = new PublishingMediaStore(properties);
    http = new PublishingHttp(new ObjectMapper(), properties);
    publisher = new SocialPublisher(social, properties, tokens, media, http);
    for (String name : List.of("youtube", "facebook", "instagram")) {
      var platform = new SocialProperties.Platform();
      platform.setBaseUrl(server.baseUrl());
      platform.setApiVersion("v26.0");
      var account = new SocialProperties.Account();
      account.setExternalId("account1");
      account.setCredentialReference("TOKEN");
      platform.setAccounts(List.of(account));
      social.getPlatforms().put(name, platform);
    }
    contentId = UUID.randomUUID().toString();
    Files.write(media.path(contentId), new byte[]{1, 2, 3, 4});
  }

  Publication job(String platform, String type, String stage, String reference, String external) {
    return new Publication("job", contentId, platform, "account1", stage, reference, external,
        "Title", "Caption", type, type.equals("IMAGE") ? "image/jpeg" : "video/mp4", 4, "public");
  }

  @Test void facebookTextAndPhotoUseCorrectEdges() {
    server.stubFor(post("/v26.0/account1/feed").withHeader("Authorization", equalTo("Bearer secret-token"))
        .withRequestBody(containing("message=Caption")).willReturn(okJson("{\"id\":\"page_post\"}")));
    var text = publisher.advance(job("facebook", "TEXT", "QUEUED", null, null));
    assertEquals("PUBLISHED", text.status());
    assertEquals("page_post", text.externalId());
    server.stubFor(post("/v26.0/account1/photos").withRequestBody(containing("url=https%3A%2F%2Fmedia.example.com"))
        .willReturn(okJson("{\"id\":\"photo\",\"post_id\":\"page_photo\"}")));
    assertEquals("page_photo", publisher.advance(job("facebook", "IMAGE", "QUEUED", null, null)).externalId());
  }

  @Test void facebookVideoWaitsForProcessing() {
    server.stubFor(post("/v26.0/account1/videos").withRequestBody(containing("file_url="))
        .willReturn(okJson("{\"id\":\"video1\"}")));
    assertEquals("CHECKING", publisher.advance(job("facebook", "VIDEO", "QUEUED", null, null)).status());
    server.stubFor(get("/v26.0/video1?fields=status").willReturn(okJson("{\"status\":{\"video_status\":\"processing\"}}")));
    var check = job("facebook", "VIDEO", "CHECKING", null, "video1");
    assertEquals("CHECKING", publisher.advance(check).status());
    server.stubFor(get("/v26.0/video1?fields=status").willReturn(okJson("{\"status\":{\"video_status\":\"ready\"}}")));
    assertEquals("PUBLISHED", publisher.advance(check).status());
  }

  @Test void instagramCreatesContainerPollsThenPublishesAndFetchesPermalink() {
    server.stubFor(post("/v26.0/account1/media").withRequestBody(containing("media_type=REELS"))
        .willReturn(okJson("{\"id\":\"container1\"}")));
    assertEquals("container1", publisher.advance(job("instagram", "VIDEO", "QUEUED", null, null)).reference());
    server.stubFor(get("/v26.0/container1?fields=status_code").willReturn(okJson("{\"status_code\":\"IN_PROGRESS\"}")));
    var processing = job("instagram", "VIDEO", "PROCESSING", "container1", null);
    assertEquals("PROCESSING", publisher.advance(processing).status());
    server.verify(0, postRequestedFor(urlEqualTo("/v26.0/account1/media_publish")));
    server.stubFor(get("/v26.0/container1?fields=status_code").willReturn(okJson("{\"status_code\":\"FINISHED\"}")));
    server.stubFor(post("/v26.0/account1/media_publish").withRequestBody(equalTo("creation_id=container1"))
        .willReturn(okJson("{\"id\":\"igpost1\"}")));
    assertEquals("CHECKING", publisher.advance(processing).status());
    server.stubFor(get("/v26.0/igpost1?fields=permalink").willReturn(okJson("{\"permalink\":\"https://www.instagram.com/p/demo/\"}")));
    assertEquals("https://www.instagram.com/p/demo/", publisher.advance(job("instagram", "VIDEO", "CHECKING", "container1", "igpost1")).permalink());
  }

  @Test void youtubePersistsSessionResumesOnlyUnacknowledgedBytesAndChecksProcessing() {
    String session = server.baseUrl() + "/upload/youtube/v3/videos?upload_id=abc";
    server.stubFor(get("/channels?part=id&mine=true").willReturn(okJson("{\"items\":[{\"id\":\"account1\"}]}")));
    server.stubFor(post("/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status")
        .withHeader("X-Upload-Content-Length", equalTo("4"))
        .withRequestBody(matchingJsonPath("$.status.privacyStatus", equalTo("public")))
        .willReturn(aResponse().withStatus(200).withHeader("Location", session)));
    assertEquals(session, publisher.advance(job("youtube", "VIDEO", "QUEUED", null, null)).reference());
    server.stubFor(put("/upload/youtube/v3/videos?upload_id=abc").withHeader("Content-Range", equalTo("bytes */4"))
        .willReturn(aResponse().withStatus(308).withHeader("Range", "bytes=0-1")));
    server.stubFor(put("/upload/youtube/v3/videos?upload_id=abc").withHeader("Content-Range", equalTo("bytes 2-3/4"))
        .withRequestBody(binaryEqualTo(new byte[]{3, 4})).willReturn(created().withHeader("Content-Type", "application/json").withBody("{\"id\":\"youtube1\"}")));
    var result = publisher.advance(job("youtube", "VIDEO", "PROCESSING", session, null));
    assertEquals("youtube1", result.externalId());
    assertEquals("CHECKING", result.status());
    server.stubFor(get("/videos?part=status&id=youtube1").willReturn(okJson("{\"items\":[{\"status\":{\"uploadStatus\":\"processed\"}}]}")));
    assertEquals("PUBLISHED", publisher.advance(job("youtube", "VIDEO", "CHECKING", null, "youtube1")).status());
  }

  @Test void youtubeRejectsWrongChannelAndForeignSessionBeforeSendingCredentials() {
    server.stubFor(get("/channels?part=id&mine=true").willReturn(okJson("{\"items\":[{\"id\":\"other\"}]}")));
    assertEquals("ACTION_REQUIRED", assertThrows(PublishFailure.class,
        () -> publisher.advance(job("youtube", "VIDEO", "QUEUED", null, null))).status());
    assertEquals("ACTION_REQUIRED", assertThrows(PublishFailure.class,
        () -> publisher.advance(job("youtube", "VIDEO", "PROCESSING", "https://attacker.example/upload", null))).status());
    server.verify(0, postRequestedFor(urlPathEqualTo("/upload/youtube/v3/videos")));
  }

  @Test void unsupportedFormatsAndMissingPublicUrlFailBeforeMutations() {
    assertEquals("FAILED", assertThrows(PublishFailure.class,
        () -> publisher.advance(job("youtube", "TEXT", "QUEUED", null, null))).status());
    assertEquals("FAILED", assertThrows(PublishFailure.class,
        () -> publisher.advance(job("instagram", "TEXT", "QUEUED", null, null))).status());
    properties.setPublicBaseUrl("");
    assertEquals("ACTION_REQUIRED", assertThrows(PublishFailure.class,
        () -> publisher.advance(job("facebook", "VIDEO", "QUEUED", null, null))).status());
  }

  @Test void ambiguousMutationIsNotAutomaticallyRetriedAndErrorsDoNotLeakTokens() {
    server.stubFor(post("/v26.0/account1/feed").willReturn(serverError().withBody("{\"error\":{\"message\":\"secret-token\"}}")));
    var failure = assertThrows(PublishFailure.class, () -> publisher.advance(job("facebook", "TEXT", "QUEUED", null, null)));
    assertEquals("UNKNOWN", failure.status());
    assertFalse(failure.getMessage().contains("secret-token"));
    server.verify(1, postRequestedFor(urlEqualTo("/v26.0/account1/feed")));
  }
}
