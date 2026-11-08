package com.slokam.social.comments;

import com.slokam.social.application.AccountCatalog;
import com.slokam.social.core.exception.ProviderException;
import com.slokam.social.infrastructure.persistence.AccountLeaseService;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@lombok.extern.slf4j.Slf4j
@ConditionalOnProperty(
    name = "social.comments.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CommentSyncService {
  private final AccountCatalog catalog;
  private final AccountLeaseService leases;
  private final CommentStore store;
  private final List<CommentProvider> providers;
  private final int budget;
  private final long refresh;

  @Value("${social.sync.enabled:true}")
  private boolean syncEnabled = true;

  public CommentSyncService(
      AccountCatalog c,
      AccountLeaseService l,
      CommentStore s,
      List<CommentProvider> p,
      @Value("${social.comments.pages-per-account:10}") int budget,
      @Value("${social.comments.refresh-seconds:900}") long refresh) {
    if (budget < 1 || refresh < 1)
      throw new IllegalArgumentException("Comment budgets and intervals must be positive");
    catalog = c;
    leases = l;
    store = s;
    providers = p;
    this.budget = budget;
    this.refresh = refresh;
  }

  @Scheduled(
      fixedDelayString = "${social.comments.interval-ms:300000}",
      initialDelayString = "${social.comments.initial-delay-ms:30000}")
  public void synchronize() {
    if (!syncEnabled) return;
    log.info("Starting scheduled comment synchronization");
    for (var a : catalog.enabledAccounts()) {
      var provider = providers.stream().filter(p -> p.supports(a.platform())).findFirst();
      if (provider.isEmpty()) continue;
      String owner = null;
      try {
        owner = leases.acquire(a.id());
        if (owner == null) {
          log.info(
              "Comment sync skipped account={} platform={} reason=account-busy",
              a.id(),
              a.platform());
          continue;
        }
        if (store.coolingDown(a.id())) {
          log.info(
              "Comment sync deferred account={} platform={} reason=provider-cooldown",
              a.id(),
              a.platform());
          continue;
        }
        store.seed(a.id(), owner);
        for (int i = 0; i < budget; i++) {
          var task = store.next(a.id());
          if (task.isEmpty()) {
            log.info("No comment tasks due account={} platform={}", a.id(), a.platform());
            break;
          }
          var t = task.get();
          try {
            leases.guard(a.id(), owner);
            if (!a.platform().equals("youtube") && a.credentialExpiresAt() != null
                && !a.credentialExpiresAt().isAfter(java.time.Instant.now()))
              throw new IllegalStateException("Expired credential");
            var page = provider.get().fetch(a, t.externalId(), t.parent(), t.cursor(), 100);
            if (page.nextCursor() != null && page.nextCursor().equals(t.cursor()))
              throw new IllegalArgumentException("Repeated comment cursor");
            store.commit(a.id(), owner, t, page, refresh);
            log.info(
                "Comment page saved account={} platform={} content={} replies={} fetched={} hasMore={}",
                a.id(),
                a.platform(),
                t.contentId(),
                !t.parent().isEmpty(),
                page.comments().size(),
                page.nextCursor() != null);
          } catch (Exception ex) {
            Duration delay = Duration.ofSeconds(refresh);
            if (ex instanceof ProviderException pe
                && pe.retryAfter() != null
                && pe.retryAfter().compareTo(delay) > 0) delay = pe.retryAfter();
            String reason =
                ex instanceof ProviderException
                    ? ex.getMessage()
                    : "Comment retrieval/persistence failed (provider bodies omitted)";
            store.fail(a.id(), owner, t, delay, reason);
            if (ex instanceof ProviderException pe && pe.retryAfter() != null)
              store.defer(a.id(), owner, delay);
            log.warn(
                "Comment sync failed account={} task={} type={} reason={}",
                a.id(),
                t.id(),
                ex.getClass().getSimpleName(),
                reason);
            // Respect account-wide provider cooldown instead of trying another video immediately.
            if (ex instanceof ProviderException pe && pe.retryAfter() != null) break;
          }
        }
      } catch (Exception ex) {
        log.warn(
            "Comment account sync failed account={} type={}",
            a.id(),
            ex.getClass().getSimpleName());
      } finally {
        if (owner != null) leases.release(a.id(), owner);
      }
    }
  }
}
