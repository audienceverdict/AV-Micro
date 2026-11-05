package com.slokam.social.replies;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.publishing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import java.util.*;
class OfficialReplyProviderTest {
  @RegisterExtension static WireMockExtension server=WireMockExtension.newInstance().options(com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig().dynamicPort()).build();
  SocialProperties properties;OfficialReplyProvider provider;SocialProperties.Account account;
  @BeforeEach void setup() {
    properties=new SocialProperties();account=new SocialProperties.Account();account.setExternalId("account");
    for(String platform:List.of("youtube","instagram","facebook")){var p=new SocialProperties.Platform();p.setBaseUrl(server.baseUrl());properties.getPlatforms().put(platform,p);}
    var tokens=mock(PublishingTokens.class);when(tokens.token(anyString(),any())).thenReturn("test-token");
    provider=new OfficialReplyProvider(properties,new PublishingHttp(new ObjectMapper(),new PublishingProperties()),tokens);
  }
  @Test void youtubeUsesOAuthAndCommentParent() {
    server.stubFor(post("/comments?part=snippet").withHeader("Authorization",equalTo("Bearer test-token"))
        .withRequestBody(equalToJson("{\"snippet\":{\"parentId\":\"parent\",\"textOriginal\":\"Thanks హలో\"}}"))
        .willReturn(okJson("{\"id\":\"reply1\"}")));
    assertEquals("reply1",provider.send("youtube",account,"parent","Thanks హలో"));
  }
  @Test void metaUsesPlatformSpecificReplyEdges() {
    for(String platform:List.of("instagram","facebook")){
      String path="/v26.0/parent/"+(platform.equals("instagram")?"replies":"comments");
      server.stubFor(post(path).withRequestBody(equalTo("message=Hello+%26+thanks")).willReturn(okJson("{\"id\":\"reply\"}")));
      assertEquals("reply",provider.send(platform,account,"parent","Hello & thanks"));
    }
  }
  @Test void ambiguousFailuresAreNeverTreatedAsSafeFailures() {
    server.stubFor(post(anyUrl()).willReturn(serverError().withBody("{}")));
    assertEquals("UNKNOWN",assertThrows(PublishFailure.class,()->provider.send("youtube",account,"parent","hello")).status());
    server.stubFor(post(anyUrl()).willReturn(okJson("{}")));
    assertEquals("UNKNOWN",assertThrows(PublishFailure.class,()->provider.send("instagram",account,"parent","hello")).status());
  }
  @Test void permissionsAndExplicitRateRejectionsAreDistinguishable() {
    server.stubFor(post(anyUrl()).willReturn(aResponse().withStatus(403).withBody("{}")));
    assertEquals("ACTION_REQUIRED",assertThrows(PublishFailure.class,()->provider.send("facebook",account,"parent","hello")).status());
    server.stubFor(post(anyUrl()).willReturn(aResponse().withStatus(429).withHeader("Retry-After","300").withBody("{}")));
    assertEquals("RETRY",assertThrows(PublishFailure.class,()->provider.send("facebook",account,"parent","hello")).status());
  }
}
