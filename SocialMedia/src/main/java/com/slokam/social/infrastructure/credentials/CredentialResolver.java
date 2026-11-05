package com.slokam.social.infrastructure.credentials;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class CredentialResolver {
  private final Environment environment;

  public CredentialResolver(Environment environment) {
    this.environment = environment;
  }

  public String required(String reference) {
    String value = optional(reference);
    if (value == null)
      throw new com.slokam.social.core.exception.ProviderException(
          "Required external credential is missing", false, null);
    return value;
  }

  public String optional(String reference) {
    if (reference == null || reference.isBlank()) return null;
    String v = environment.getProperty(reference);
    return v == null || v.isBlank() ? null : v;
  }
}
