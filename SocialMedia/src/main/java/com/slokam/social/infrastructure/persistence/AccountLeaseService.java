package com.slokam.social.infrastructure.persistence;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountLeaseService {
  private final JdbcTemplate jdbc;

  public AccountLeaseService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public String acquire(Long id) {
    jdbc.update(
        "INSERT IGNORE INTO social_account_leases(account_id,expires_at) VALUES (?,UTC_TIMESTAMP(6))",
        id);
    var row =
        jdbc.queryForMap(
            "SELECT owner, expires_at <= UTC_TIMESTAMP(6) AS expired FROM social_account_leases WHERE account_id=? FOR UPDATE",
            id);
    if (((Number) row.get("expired")).intValue() == 0) return null;
    jdbc.update(
        "UPDATE social_sync_jobs SET status='ABANDONED',completed_at=UTC_TIMESTAMP(6),error_message='Previous worker lease expired' WHERE account_id=? AND status='RUNNING'",
        id);
    String owner = UUID.randomUUID().toString();
    jdbc.update(
        "UPDATE social_account_leases SET owner=?,expires_at=TIMESTAMPADD(MINUTE,15,UTC_TIMESTAMP(6)) WHERE account_id=?",
        owner,
        id);
    return owner;
  }

  // This row lock is held through the page transaction, fencing stale workers after lease takeover.
  @Transactional
  public void guard(Long id, String owner) {
    var row =
        jdbc.queryForMap(
            "SELECT owner,expires_at > UTC_TIMESTAMP(6) AS valid FROM social_account_leases WHERE account_id=? FOR UPDATE",
            id);
    if (!Objects.equals(owner, row.get("owner")) || ((Number) row.get("valid")).intValue() == 0)
      throw new IllegalStateException("Account synchronization lease lost");
    jdbc.update(
        "UPDATE social_account_leases SET expires_at=TIMESTAMPADD(MINUTE,15,UTC_TIMESTAMP(6)) WHERE account_id=?",
        id);
  }

  @Transactional
  public void release(Long id, String owner) {
    jdbc.update(
        "UPDATE social_account_leases SET owner=NULL,expires_at=UTC_TIMESTAMP(6) WHERE account_id=? AND owner=?",
        id,
        owner);
  }
}
