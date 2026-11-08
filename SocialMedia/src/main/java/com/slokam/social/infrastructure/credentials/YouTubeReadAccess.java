package com.slokam.social.infrastructure.credentials;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.core.exception.ProviderException;
import com.slokam.social.core.model.SocialAccount;
import com.slokam.social.infrastructure.resilience.ProviderHttpClient;
import com.slokam.social.publishing.PublishFailure;
import com.slokam.social.publishing.PublishingTokens;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Shares the publishing OAuth cache with content, comments and owner analytics. */
@Component
public class YouTubeReadAccess {
  private final SocialProperties properties;
  private final CredentialResolver credentials;
  private final PublishingTokens tokens;
  private final ProviderHttpClient http;

  public YouTubeReadAccess(SocialProperties properties, CredentialResolver credentials,
      PublishingTokens tokens, ProviderHttpClient http) {
    this.properties = properties;
    this.credentials = credentials;
    this.tokens = tokens;
    this.http = http;
  }

  public JsonNode get(SocialAccount account, String operation, Map<String, String> query) {
    var configured = configured(account);
    boolean refresh = credentials.optional(configured.getRefreshTokenReference()) != null;
    String key = credentials.optional(configured.getApiKeyReference());
    // API-key-only reads avoid sending a stale bearer token with public requests.
    if (!refresh && key != null) return http.get("youtube", operation, query, null, key);
    return authorized(account, configured, refresh,
        token -> http.get("youtube", operation, query, token, null));
  }

  public JsonNode analytics(SocialAccount account, Map<String, String> query) {
    var configured = configured(account);
    return authorized(account, configured,
        credentials.optional(configured.getRefreshTokenReference()) != null,
        token -> http.youtubeAnalytics(query, token));
  }

  private SocialProperties.Account configured(SocialAccount account) {
    return properties.getPlatforms().get("youtube").getAccounts().stream()
        .filter(a -> a.getExternalId().equals(account.externalAccountId()))
        .findFirst().orElseThrow();
  }

  private JsonNode authorized(SocialAccount account, SocialProperties.Account configured,
      boolean refresh, Function<String, JsonNode> request) {
    String token = accessToken(account, configured, refresh);
    try {
      return request.apply(token);
    } catch (ProviderException failure) {
      if (!refresh || !failure.category().equals("AUTH_REQUIRED")) throw failure;
      // Only read requests are replayed, once. Permissions and quota errors are not refreshed.
      tokens.invalidate("youtube", account.externalAccountId());
      return request.apply(accessToken(account, configured, true));
    }
  }

  private String accessToken(SocialAccount account, SocialProperties.Account configured, boolean refresh) {
    if (refresh) {
      try {
        return tokens.token("youtube", configured);
      } catch (PublishFailure failure) {
        boolean retryable = failure.status().equals("RETRY");
        throw new ProviderException("YouTube OAuth renewal failed; "
            + (retryable ? "retry later" : "check OAuth client settings and reconnect the account"),
            retryable, retryable ? Duration.ofSeconds(failure.retrySeconds()) : null,
            retryable ? "FAILED" : "AUTH_REQUIRED");
      }
    }
    String token = credentials.optional(account.credentialReference());
    if (token == null || (account.credentialExpiresAt() != null
        && !account.credentialExpiresAt().isAfter(Instant.now())))
      throw new ProviderException("YouTube requires an API key for public reads or offline OAuth authorization",
          false, null, "AUTH_REQUIRED");
    return token;
  }
}
