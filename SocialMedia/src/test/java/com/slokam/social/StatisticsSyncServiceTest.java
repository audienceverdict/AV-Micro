package com.slokam.social;

import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slokam.social.application.AccountCatalog;
import com.slokam.social.comments.CommentStore;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.core.exception.ProviderException;
import com.slokam.social.core.model.SocialAccount;
import com.slokam.social.infrastructure.persistence.AccountLeaseService;
import com.slokam.social.statistics.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class StatisticsSyncServiceTest {
  final AccountCatalog catalog=mock(AccountCatalog.class);
  final AccountLeaseService leases=mock(AccountLeaseService.class);
  final StatisticsStore store=mock(StatisticsStore.class);
  final StatisticsProvider provider=mock(StatisticsProvider.class);
  final CommentStore cooldowns=mock(CommentStore.class);
  final SocialProperties props=new SocialProperties();
  final SocialAccount account=new SocialAccount(1L,"youtube","channel","name","TOKEN",false,null);
  final StatisticsStore.Task first=new StatisticsStore.Task(1,new StatisticsProvider.Target(1,"v","VIDEO",new ObjectMapper().createObjectNode()),"public",null,LocalDate.now().minusDays(28),LocalDate.now().minusDays(1));
  final StatisticsStore.Task second=new StatisticsStore.Task(2,first.target(),"analytics:views",null,first.from(),first.to());
  StatisticsSyncService service() {
    var p=new SocialProperties.Platform(); var a=new SocialProperties.Account(); a.setExternalId("channel");p.setAccounts(List.of(a));props.getPlatforms().put("youtube",p);
    when(catalog.enabledAccounts()).thenReturn(List.of(account));when(leases.acquire(1L)).thenReturn("owner");
    when(provider.supports("youtube")).thenReturn(true);
    when(store.start(anyLong(),anyString(),any(),anyInt())).thenAnswer(i->i.getArgument(2));
    return new StatisticsSyncService(catalog,leases,store,List.of(provider),props,cooldowns);
  }
  @Test void rejectedMetricDoesNotPreventNextMetricFromSucceeding() {
    var service=service();
    when(store.next(account)).thenReturn(Optional.of(first),Optional.of(second),Optional.empty());
    when(provider.fetch(account,first.target(),first.operation(),null,first.from(),first.to()))
        .thenThrow(new ProviderException("Provider HTTP 403",false,null,"PERMISSION_DENIED"));
    var page=new StatisticsProvider.Page(new ObjectMapper().createObjectNode(),null);
    when(provider.fetch(account,second.target(),second.operation(),null,second.from(),second.to())).thenReturn(page);
    service.synchronize();
    verify(store).fail(1,"owner",first,"PERMISSION_DENIED",86400);
    verify(store).commit(1,"owner",second,page,3600,86400,100);
    verify(leases).release(1L,"owner");verify(cooldowns,never()).defer(anyLong(),anyString(),any());
  }
  @Test void throttlingStopsFurtherRequestsAndDefersAccount() {
    var service=service();when(store.next(account)).thenReturn(Optional.of(first));
    when(provider.fetch(any(),any(),anyString(),isNull(),any(),any()))
        .thenThrow(new ProviderException("Rate limit",true,Duration.ofHours(1)));
    service.synchronize();
    verify(cooldowns).defer(1,"owner",Duration.ofHours(1));verify(store,times(1)).next(account);
    verify(leases).release(1L,"owner");
  }
  @Test void disabledGlobalSyncMakesNoProviderCalls() {
    var service=service();props.getSync().setEnabled(false);service.synchronize();
    verify(catalog,never()).enabledAccounts();verifyNoInteractions(store);
  }
}
