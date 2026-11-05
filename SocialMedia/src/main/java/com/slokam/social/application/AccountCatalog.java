package com.slokam.social.application;

import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.core.model.SocialAccount;
import com.slokam.social.infrastructure.persistence.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountCatalog {
  private final SocialProperties properties;
  private final AccountRepository accounts;
  private final CredentialRepository credentials;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public AccountCatalog(
      SocialProperties p,
      AccountRepository a,
      CredentialRepository c,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    properties = p;
    accounts = a;
    credentials = c;
    this.jdbc = jdbc;
  }

  @Transactional
  public List<SocialAccount> enabledAccounts() {
    var result = new ArrayList<SocialAccount>();
    properties
        .getPlatforms()
        .forEach(
            (platform, cfg) -> {
              if (cfg.isEnabled())
                for (var configured : cfg.getAccounts()) {
                  jdbc.update(
                      "INSERT INTO social_accounts(platform,external_account_id,account_name,enabled,created_at,updated_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE account_name=VALUES(account_name),enabled=VALUES(enabled),updated_at=UTC_TIMESTAMP(6)",
                      platform,
                      configured.getExternalId(),
                      configured.getName(),
                      configured.isEnabled());
                  var a =
                      accounts
                          .findByPlatformAndExternalAccountId(platform, configured.getExternalId())
                          .orElseThrow();
                  jdbc.update(
                      "INSERT INTO social_credentials(account_id,credential_reference,created_at,updated_at) VALUES(?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE credential_reference=VALUES(credential_reference),updated_at=UTC_TIMESTAMP(6)",
                      a.id,
                      configured.getCredentialReference());
                  var credential = credentials.findByAccountId(a.id).orElseThrow();
                  if (a.enabled)
                    result.add(
                        new SocialAccount(
                            a.id,
                            platform,
                            a.externalAccountId,
                            a.accountName,
                            credential.credentialReference,
                            configured.isStoriesEnabled(),
                            credential.expiresAt));
                }
            });
    return result;
  }
}
