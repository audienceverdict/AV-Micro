package com.slokam.social;

import static org.mockito.Mockito.*;

import com.slokam.social.application.AccountCatalog;
import com.slokam.social.comments.*;
import com.slokam.social.core.exception.ProviderException;
import com.slokam.social.core.model.SocialAccount;
import com.slokam.social.infrastructure.persistence.AccountLeaseService;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

class CommentSyncServiceTest {
  @Test
  void failurePreservesProgressAndAppliesAccountCooldown() {
    var catalog = mock(AccountCatalog.class);
    var leases = mock(AccountLeaseService.class);
    var store = mock(CommentStore.class);
    var provider = mock(CommentProvider.class);
    var account = new SocialAccount(1L, "youtube", "channel", "name", "TOKEN", false, null);
    var task = new CommentStore.Task(2, 3, "video", "parent", "resume");
    when(catalog.enabledAccounts()).thenReturn(List.of(account));
    when(provider.supports("youtube")).thenReturn(true);
    when(leases.acquire(1L)).thenReturn("owner");
    when(store.next(1)).thenReturn(Optional.of(task));
    when(provider.fetch(account, "video", "parent", "resume", 100))
        .thenThrow(new ProviderException("Rate limited", false, Duration.ofHours(1)));
    new CommentSyncService(catalog, leases, store, List.of(provider), 10, 900).synchronize();
    verify(store).fail(1, "owner", task, Duration.ofHours(1), "Rate limited");
    verify(store).defer(1, "owner", Duration.ofHours(1));
    verify(store, never()).commit(anyLong(), anyString(), any(), any(), anyLong());
    verify(leases).release(1L, "owner");
    verify(store, times(1)).next(1);
  }

  @Test
  void existingCursorIsResumedAndBudgetBoundsRequests() {
    var catalog = mock(AccountCatalog.class);
    var leases = mock(AccountLeaseService.class);
    var store = mock(CommentStore.class);
    var provider = mock(CommentProvider.class);
    var a = new SocialAccount(1L, "youtube", "channel", "name", "TOKEN", false, null);
    var task = new CommentStore.Task(2, 3, "video", "", "resume");
    var page = new CommentPage(List.of(), "next");
    when(catalog.enabledAccounts()).thenReturn(List.of(a));
    when(leases.acquire(1L)).thenReturn("owner");
    when(provider.supports("youtube")).thenReturn(true);
    when(store.next(1)).thenReturn(Optional.of(task));
    when(provider.fetch(a, "video", "", "resume", 100)).thenReturn(page);
    new CommentSyncService(catalog, leases, store, List.of(provider), 1, 900).synchronize();
    verify(provider, times(1)).fetch(a, "video", "", "resume", 100);
    verify(store).commit(1, "owner", task, page, 900);
  }
}
