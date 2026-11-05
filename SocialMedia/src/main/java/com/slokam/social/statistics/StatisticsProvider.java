package com.slokam.social.statistics;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.core.model.SocialAccount;
import java.time.LocalDate;
import java.util.List;

public interface StatisticsProvider {
  record Target(long contentId, String externalId, String contentType, JsonNode metadata) {
    public boolean account() { return contentId == 0; }
  }
  record Page(JsonNode data, String nextCursor) {}
  boolean supports(String platform);
  List<String> operations(SocialAccount account, Target target);
  Page fetch(SocialAccount account, Target target, String operation, String cursor,
      LocalDate from, LocalDate to);
}
