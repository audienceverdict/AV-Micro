package com.slokam.social.infrastructure.credentials;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.core.exception.ProviderException;
import com.slokam.social.core.model.SocialAccount;
import com.slokam.social.infrastructure.resilience.ProviderHttpClient;
import com.slokam.social.publishing.*;
import java.net.http.HttpHeaders;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class YouTubeReadAccessTest {
  final SocialProperties properties = new SocialProperties();
  final SocialProperties.Account configured = new SocialProperties.Account();
  final MockEnvironment environment = new MockEnvironment();
  final ProviderHttpClient http = mock(ProviderHttpClient.class);
  final PublishingHttp oauth = mock(PublishingHttp.class);
  final PublishingProperties publishing = new PublishingProperties();
  final ObjectMapper json = new ObjectMapper();
  final SocialAccount account = new SocialAccount(1L, "youtube", "channel", "Channel", "ACCESS", false,
      Instant.parse("2020-01-01T00:00:00Z"));
  PublishingTokens tokens;
  YouTubeReadAccess access;

  @BeforeEach void setup() {
    configured.setExternalId("channel");
    configured.setCredentialReference("ACCESS");
    configured.setRefreshTokenReference("REFRESH");
    configured.setApiKeyReference("KEY");
    var platform = new SocialProperties.Platform();
    platform.setAccounts(List.of(configured));
    properties.getPlatforms().put("youtube", platform);
    publishing.getYoutube().setClientId("client");
    publishing.getYoutube().setClientSecret("secret");
    var resolver = new CredentialResolver(environment);
    tokens = new PublishingTokens(publishing, resolver, oauth);
    access = new YouTubeReadAccess(properties, resolver, tokens, http);
  }

  PublishingHttp.Response response(String token, long lifetime) throws Exception {
    return new PublishingHttp.Response(200, HttpHeaders.of(Map.of(), (a,b)->true),
        json.readTree("{\"access_token\":\"" + token + "\",\"expires_in\":" + lifetime + "}"));
  }

  @Test void contentAnalyticsAndPublishingShareRenewedTokenDespiteExpiredStoredCredential() throws Exception {
    environment.withProperty("REFRESH", "offline").withProperty("ACCESS", "expired");
    when(oauth.form(anyString(), isNull(), anyMap(), eq(false))).thenReturn(response("fresh", 3600));
    access.get(account, "channels", Map.of());
    access.analytics(account, Map.of());
    assertEquals("fresh", tokens.token("youtube", configured));
    verify(http).get("youtube", "channels", Map.of(), "fresh", null);
    verify(http).youtubeAnalytics(Map.of(), "fresh");
    verify(oauth, times(1)).form(anyString(), isNull(), argThat(form ->
        "offline".equals(form.get("refresh_token")) && "client".equals(form.get("client_id"))), eq(false));
  }

  @Test void expiredCachedTokenIsRenewedOnNextRead() throws Exception {
    environment.withProperty("REFRESH", "offline");
    when(oauth.form(anyString(), isNull(), anyMap(), eq(false)))
        .thenReturn(response("short", 0), response("fresh", 3600));
    access.get(account, "channels", Map.of());
    access.get(account, "videos", Map.of());
    verify(http).get("youtube", "videos", Map.of(), "fresh", null);
    verify(oauth, times(2)).form(anyString(), isNull(), anyMap(), eq(false));
  }

  @Test void authenticationRejectionRefreshesOnceAndDoesNotLoop() throws Exception {
    environment.withProperty("REFRESH", "offline");
    when(oauth.form(anyString(), isNull(), anyMap(), eq(false)))
        .thenReturn(response("first", 3600), response("second", 3600));
    when(http.get(eq("youtube"), anyString(), anyMap(), anyString(), isNull()))
        .thenThrow(new ProviderException("unauthorized", false, null, "AUTH_REQUIRED"));
    assertThrows(ProviderException.class, () -> access.get(account, "channels", Map.of()));
    verify(http).get("youtube", "channels", Map.of(), "first", null);
    verify(http).get("youtube", "channels", Map.of(), "second", null);
    verify(oauth, times(2)).form(anyString(), isNull(), anyMap(), eq(false));
  }

  @Test void analyticsAlsoRecoversFromRejectedCachedToken() throws Exception {
    environment.withProperty("REFRESH", "offline");
    when(oauth.form(anyString(), isNull(), anyMap(), eq(false)))
        .thenReturn(response("first", 3600), response("second", 3600));
    when(http.youtubeAnalytics(Map.of(), "first"))
        .thenThrow(new ProviderException("unauthorized", false, null, "AUTH_REQUIRED"));
    access.analytics(account, Map.of());
    verify(http).youtubeAnalytics(Map.of(), "second");
  }

  @Test void permissionsAndQuotaDoNotTriggerTokenRefresh() throws Exception {
    environment.withProperty("REFRESH", "offline");
    when(oauth.form(anyString(), isNull(), anyMap(), eq(false))).thenReturn(response("fresh", 3600));
    for (String category : List.of("PERMISSION_DENIED", "QUOTA_EXCEEDED")) {
      doThrow(new ProviderException("rejected", false, null, category))
          .when(http).youtubeAnalytics(anyMap(), anyString());
      assertEquals(category, assertThrows(ProviderException.class,
          () -> access.analytics(account, Map.of())).category());
    }
    verify(oauth, times(1)).form(anyString(), isNull(), anyMap(), eq(false));
  }

  @Test void publicApiKeyDoesNotSendExpiredBearerAndCannotAuthorizeAnalytics() {
    environment.withProperty("KEY", "public-key").withProperty("ACCESS", "expired");
    access.get(account, "videos", Map.of());
    verify(http).get("youtube", "videos", Map.of(), null, "public-key");
    assertEquals("AUTH_REQUIRED", assertThrows(ProviderException.class,
        () -> access.analytics(account, Map.of())).category());
    verifyNoInteractions(oauth);
  }

  @Test void revokedRefreshTokenRequiresReconnectWithoutLeakingSecrets() {
    environment.withProperty("REFRESH", "offline-secret");
    when(oauth.form(anyString(), isNull(), anyMap(), eq(false)))
        .thenThrow(new PublishFailure("FAILED", "sensitive remote response"));
    var failure = assertThrows(ProviderException.class, () -> access.get(account, "videos", Map.of()));
    assertEquals("AUTH_REQUIRED", failure.category());
    assertFalse(failure.getMessage().contains("sensitive"));
    assertFalse(failure.getMessage().contains("offline-secret"));
    verifyNoInteractions(http);
  }
}
