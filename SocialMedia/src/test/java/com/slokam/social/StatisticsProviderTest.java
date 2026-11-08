package com.slokam.social;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.core.model.SocialAccount;
import com.slokam.social.infrastructure.credentials.CredentialResolver;
import com.slokam.social.infrastructure.resilience.ProviderHttpClient;
import com.slokam.social.statistics.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class StatisticsProviderTest {
  final ObjectMapper json=new ObjectMapper();
  final ProviderHttpClient http=mock(ProviderHttpClient.class);
  final CredentialResolver credentials=mock(CredentialResolver.class);
  final SocialProperties props=new SocialProperties();
  final OfficialStatisticsProvider provider=new OfficialStatisticsProvider(http,credentials,props,
      new com.slokam.social.infrastructure.credentials.YouTubeReadAccess(props,credentials,
          mock(com.slokam.social.publishing.PublishingTokens.class),http));
  final LocalDate from=LocalDate.of(2026,10,1), to=LocalDate.of(2026,10,28);
  SocialAccount account(String platform) {
    var cfg=new SocialProperties.Platform(); cfg.setApiVersion(platform.equals("linkedin") ? "202610" : "v26.0");
    var ac=new SocialProperties.Account(); ac.setExternalId(platform.equals("linkedin") ? "urn:li:organization:123" : "123");
    ac.setApiKeyReference("KEY"); cfg.setAccounts(List.of(ac)); props.getPlatforms().put(platform,cfg);
    when(credentials.required("TOKEN")).thenReturn("secret");
    return new SocialAccount(1L,platform,ac.getExternalId(),"name","TOKEN",false,null);
  }
  StatisticsProvider.Target target(long id) {
    return new StatisticsProvider.Target(id,"123",id==0?"ACCOUNT":"VIDEO",json.createObjectNode());
  }
  @Test void allPlatformsHaveAccountAndContentOperationsAndYoutubeSkipsPlaylists() {
    for(var p:List.of("youtube","instagram","facebook","linkedin")) {
      var a=account(p);
      assertFalse(provider.operations(a,target(0)).isEmpty());
      var t=p.equals("linkedin") ? new StatisticsProvider.Target(1,"urn:li:share:9","TEXT",json.createObjectNode()) : target(1);
      assertFalse(provider.operations(a,t).isEmpty());
    }
    assertTrue(provider.operations(account("youtube"),new StatisticsProvider.Target(1,"playlist","UNKNOWN",json.createObjectNode())).isEmpty());
    assertFalse(provider.operations(account("youtube"),target(0)).contains("report:retention"));
    assertTrue(provider.operations(account("youtube"),target(1)).contains("report:retention"));
  }
  @Test void apiKeyRetrievesPublicStatisticsButCannotBeUsedForOwnerAnalytics() throws Exception {
    var a=account("youtube"); when(credentials.optional("KEY")).thenReturn("api-key");
    when(http.get(eq("youtube"),eq("videos"),anyMap(),isNull(),eq("api-key")))
        .thenReturn(json.readTree("{\"items\":[{\"id\":\"123\",\"statistics\":{\"viewCount\":\"0\"}}]}"));
    var page=provider.fetch(a,target(1),"public",null,from,to);
    assertEquals("0",page.data().path("items").path(0).path("statistics").path("viewCount").asText());
    assertThrows(com.slokam.social.core.exception.ProviderException.class,
        ()->provider.fetch(a,target(1),"analytics:views",null,from,to));
    verify(http,never()).youtubeAnalytics(anyMap(),anyString());
  }
  @Test void analyticsIncludesReportingWindowVideoFilterAndDimensions() throws Exception {
    var a=account("youtube"); when(credentials.optional("TOKEN")).thenReturn("owner");
    when(http.youtubeAnalytics(anyMap(),eq("owner"))).thenReturn(json.readTree("{\"columnHeaders\":[{\"name\":\"country\"}],\"rows\":[[\"IN\",4]]}"));
    var result=provider.fetch(a,target(1),"report:geography",null,from,to);
    assertEquals("IN",result.data().path("rows").path(0).path(0).asText());
    verify(http).youtubeAnalytics(argThat(q->q.get("filters").equals("video==123")
        && q.get("ids").equals("channel==123") && q.get("startDate").equals("2026-10-01")
        && q.get("dimensions").equals("country")),eq("owner"));
  }
  @Test void graphKeepsStructuredInsightsAndNeverPersistsPaginationTokens() throws Exception {
    var a=account("instagram");
    when(http.get(eq("instagram"),eq("v26.0/123/insights"),anyMap(),eq("secret"),isNull()))
        .thenReturn(json.readTree("{\"data\":[{\"name\":\"reach\",\"period\":\"lifetime\",\"values\":[{\"value\":0}]}],\"paging\":{\"next\":\"https://graph.facebook.com/next?access_token=secret&after=next-page\",\"cursors\":{\"after\":\"next-page\"}}}"));
    var page=provider.fetch(a,target(1),"insight:reach",null,from,to);
    assertEquals("next-page",page.nextCursor()); assertFalse(page.data().toString().contains("secret"));
    assertEquals(0,page.data().path("data").path(0).path("values").path(0).path("value").asInt());
  }
  @Test void facebookVideoInsightsUseAttachmentVideoIdentityRatherThanPostId() throws Exception {
    var a=account("facebook");
    var t=new StatisticsProvider.Target(1,"page_post","VIDEO",json.readTree("{\"attachments\":[{\"type\":\"video_inline\",\"target\":{\"id\":\"456\"}}]}"));
    assertTrue(provider.operations(a,t).contains("video:456:total_video_views"));
    when(http.get(eq("facebook"),eq("v26.0/456/video_insights"),anyMap(),eq("secret"),isNull())).thenReturn(json.readTree("{\"data\":[]}"));
    provider.fetch(a,t,"video:456:total_video_views",null,from,to);
    verify(http).get(eq("facebook"),eq("v26.0/456/video_insights"),argThat(q->q.get("metric").equals("total_video_views")),eq("secret"),isNull());
  }
  @Test void malformedResponseAndUnconfiguredOperationsCannotSucceed() throws Exception {
    var a=account("instagram");
    when(http.get(anyString(),anyString(),anyMap(),anyString(),isNull())).thenReturn(json.readTree("{}"));
    assertThrows(IllegalArgumentException.class,()->provider.fetch(a,target(1),"insight:reach",null,from,to));
    assertThrows(IllegalArgumentException.class,()->provider.fetch(a,target(1),"insight:invented",null,from,to));
  }
  @Test void linkedinKeepsNestedDemographicsAndUsesVersionHeaders() throws Exception {
    var a=account("linkedin");
    when(http.get(eq("linkedin"),eq("organizationalEntityFollowerStatistics"),anyMap(),eq("secret"),isNull(),anyMap()))
        .thenReturn(json.readTree("{\"elements\":[{\"followerCountsByFunction\":[{\"organicFollowerCount\":100}]}]}"));
    assertEquals(100,provider.fetch(a,target(0),"followers",null,from,to).data()
        .path("elements").path(0).path("followerCountsByFunction").path(0).path("organicFollowerCount").asInt());
    verify(http).get(eq("linkedin"),anyString(),argThat(q->q.get("organizationalEntity").equals(a.externalAccountId())),eq("secret"),isNull(),argThat(h->h.get("Linkedin-Version").equals("202610")));
  }
  @Test void customInsightsRetainBreakdownAndUseProviderTimeframe() throws Exception {
    var a=account("instagram");
    var report=new SocialProperties.InsightReport();report.setPlatform("instagram");
    report.setMetric("follower_demographics");report.setPeriod("lifetime");
    report.setMetricType("total_value");report.setBreakdown("age");report.setTimeframe("last_30_days");
    props.getStatistics().getInsightReports().put("audience",report);
    when(http.get(eq("instagram"),eq("v26.0/123/insights"),anyMap(),eq("secret"),isNull()))
        .thenReturn(json.readTree("{\"data\":[]}"));
    provider.fetch(a,target(0),"custom:audience",null,from,to);
    verify(http).get(eq("instagram"),anyString(),argThat(q->q.get("breakdown").equals("age")
        && q.get("period").equals("lifetime") && q.get("timeframe").equals("last_30_days")
        && !q.containsKey("since") && !q.containsKey("until")),eq("secret"),isNull());
  }

}
