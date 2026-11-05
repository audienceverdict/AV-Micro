package com.slokam.social.statistics;

import com.slokam.social.application.AccountCatalog;
import com.slokam.social.comments.CommentStore;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.core.exception.ProviderException;
import com.slokam.social.infrastructure.persistence.AccountLeaseService;
import java.time.Duration;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@lombok.extern.slf4j.Slf4j
public class StatisticsSyncService {
  private final AccountCatalog catalog;
  private final AccountLeaseService leases;
  private final StatisticsStore store;
  private final List<StatisticsProvider> providers;
  private final SocialProperties properties;
  private final CommentStore cooldowns;
  public StatisticsSyncService(AccountCatalog c, AccountLeaseService l, StatisticsStore s,
      List<StatisticsProvider> providers, SocialProperties p, CommentStore cooldowns) {
    catalog=c; leases=l; store=s; this.providers=providers; properties=p; this.cooldowns=cooldowns;
  }

  @Scheduled(fixedDelayString="${social.statistics.interval-ms:300000}",
      initialDelayString="${social.statistics.initial-delay-ms:60000}")
  public void synchronize() {
    var s = properties.getStatistics();
    if (!properties.getSync().isEnabled() || !s.isEnabled()) return;
    for (var a : catalog.enabledAccounts()) {
      var matches = providers.stream().filter(p -> p.supports(a.platform())).toList();
      if (matches.size() != 1) {
        log.warn("Statistics provider unavailable or ambiguous platform={}", a.platform()); continue;
      }
      String owner = null;
      try {
        owner = leases.acquire(a.id());
        if (owner == null || cooldowns.coolingDown(a.id())) continue;
        boolean enabled = properties.getPlatforms().get(a.platform()).getAccounts().stream()
            .anyMatch(c -> c.getExternalId().equals(a.externalAccountId()) && c.isStatisticsEnabled());
        store.seed(a, owner, matches.getFirst(), enabled);
        if (!enabled) continue;
        for (int i=0; i<s.getRequestsPerAccount(); i++) {
          var due = store.next(a);
          if (due.isEmpty()) break;
          var t = store.start(a.id(), owner, due.get(), s.getLookbackDays());
          try {
            if (a.credentialExpiresAt()!=null && !a.credentialExpiresAt().isAfter(java.time.Instant.now())
                && !(a.platform().equals("youtube") && t.operation().equals("public")))
              throw new OfficialStatisticsProvider.AccessUnavailable();
            var page = matches.getFirst().fetch(a, t.target(), t.operation(), t.cursor(), t.from(), t.to());
            store.commit(a.id(), owner, t, page, s.getRefreshSeconds(), s.getSnapshotSeconds(), s.getMaxPages());
          } catch (Exception e) {
            String status = e instanceof OfficialStatisticsProvider.AccessUnavailable ? "AUTH_REQUIRED"
                : e instanceof ProviderException pe ? pe.category() : "FAILED";
            store.fail(a.id(), owner, t, status, s.getFailureRetrySeconds());
            log.warn("Statistics operation failed account={} task={} status={}", a.id(), t.id(), status);
            if (e instanceof ProviderException pe && (pe.retryable() || pe.retryAfter()!=null
                || pe.getMessage().contains("quota"))) {
              Duration delay = pe.retryAfter()==null ? Duration.ofMinutes(15) : pe.retryAfter();
              cooldowns.defer(a.id(), owner, delay); break;
            }
          }
        }
      } catch (Exception e) {
        log.warn("Statistics account synchronization failed account={} exceptionType={}", a.id(), e.getClass().getSimpleName());
      } finally { if (owner != null) leases.release(a.id(), owner); }
    }
  }
}
