package com.slokam.social.assignments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AudienceDirectory {
  private final RestClient client;
  public AudienceDirectory(@org.springframework.beans.factory.annotation.Qualifier("audienceIdentityClient") RestClient client) { this.client = client; }
  public record Account(String id, String name, boolean enabled) {}
  public Account requireEnabled(String id) {
    try {
      Account account = client.get().uri("/internal/users/{id}", id).retrieve().body(Account.class);
      if (account == null || !account.enabled())
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient account is disabled or unavailable");
      return account;
    } catch (HttpClientErrorException.NotFound e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient user not found");
    } catch (RestClientException | IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity service unavailable");
    }
  }
  @Configuration
  static class ClientConfiguration {
    @Bean @LoadBalanced RestClient.Builder audienceIdentityBuilder() {
      var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(3000); factory.setReadTimeout(5000);
      return RestClient.builder().requestFactory(factory);
    }
    @Bean RestClient audienceIdentityClient(
        @org.springframework.beans.factory.annotation.Qualifier("audienceIdentityBuilder") RestClient.Builder builder,
        @Value("${internal.identity-url:http://identity-service}") String url,
        @Value("${internal.service-token:}") String token) {
      return builder.baseUrl(url).defaultHeader("X-Service-Token", token).build();
    }
  }
}
