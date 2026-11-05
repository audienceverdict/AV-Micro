package com.slokam.social.publishing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.infrastructure.credentials.CredentialResolver;
import java.net.http.HttpHeaders;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PublishingTokensTest {
  @Test void refreshTokenUsesConfiguredClientAndCachesUntilInvalidated() throws Exception {
    var properties = new PublishingProperties();
    properties.getYoutube().setClientId("client-id");
    properties.getYoutube().setClientSecret("client-secret");
    var resolver = mock(CredentialResolver.class);
    when(resolver.optional("REFRESH")).thenReturn("refresh-value");
    var http = mock(PublishingHttp.class);
    when(http.form(anyString(), isNull(), anyMap(), eq(false))).thenReturn(new PublishingHttp.Response(200,
        HttpHeaders.of(Map.of(), (a,b) -> true), new ObjectMapper().readTree("{\"access_token\":\"access\",\"expires_in\":3600}")));
    var tokens = new PublishingTokens(properties, resolver, http);
    var account = new SocialProperties.Account();
    account.setExternalId("channel1");
    account.setRefreshTokenReference("REFRESH");
    assertEquals("access", tokens.token("youtube", account));
    assertEquals("access", tokens.token("youtube", account));
    verify(http, times(1)).form(eq("https://oauth2.googleapis.com/token"), isNull(), eq(Map.of(
        "grant_type", "refresh_token", "client_id", "client-id", "client_secret", "client-secret", "refresh_token", "refresh-value")), eq(false));
    tokens.invalidate("youtube", "channel1");
    tokens.token("youtube", account);
    verify(http, times(2)).form(anyString(), isNull(), anyMap(), eq(false));
  }

  @Test void missingCredentialsAreActionRequired() {
    var tokens = new PublishingTokens(new PublishingProperties(), mock(CredentialResolver.class), mock(PublishingHttp.class));
    assertEquals("ACTION_REQUIRED", assertThrows(PublishFailure.class,
        () -> tokens.token("facebook", new SocialProperties.Account())).status());
  }
}
