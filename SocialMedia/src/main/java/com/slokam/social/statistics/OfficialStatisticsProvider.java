package com.slokam.social.statistics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.core.model.SocialAccount;
import com.slokam.social.infrastructure.credentials.CredentialResolver;
import com.slokam.social.infrastructure.resilience.ProviderHttpClient;
import com.slokam.social.providers.GraphApiClient;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.stereotype.Component;

/** Independent requests keep unsupported/permission-limited metrics from hiding other results. */
@Component
public class OfficialStatisticsProvider extends GraphApiClient implements StatisticsProvider {
  public OfficialStatisticsProvider(ProviderHttpClient h, CredentialResolver c, SocialProperties p) {
    super(h, c, p);
  }

  public boolean supports(String platform) {
    return Set.of("youtube", "instagram", "facebook", "linkedin").contains(platform);
  }

  public List<String> operations(SocialAccount a, Target t) {
    var s = properties.getStatistics();
    var result = new ArrayList<String>();
    switch (a.platform()) {
      case "youtube" -> {
        if (!t.account() && !Set.of("VIDEO", "SHORT_VIDEO", "LIVE", "REEL").contains(t.contentType()))
          return List.of();
        result.add("public");
        add(result, "analytics:", s.getYoutubeAnalyticsMetrics());
        s.getYoutubeReports().forEach((name, report) -> {
          if (!t.account() || !report.isContentOnly()) { validate(name.replace('-', '_')); result.add("report:" + name); }
        });
      }
      case "instagram" -> {
        result.addAll(t.account() ? List.of("field:followers_count", "field:follows_count", "field:media_count")
            : List.of("field:like_count", "field:comments_count"));
        add(result, "insight:", t.account() ? s.getInstagramAccountMetrics() : s.getInstagramMediaMetrics());
      }
      case "facebook" -> {
        if (t.account()) {
          // Request account fields separately: an unsupported field cannot block followers.
          result.addAll(List.of("field:followers_count", "field:fan_count"));
          add(result, "insight:", s.getFacebookAccountMetrics());
        } else {
          result.addAll(List.of("field:reactions.limit(0).summary(true)", "field:comments.limit(0).summary(true)", "field:shares"));
          for (String reaction : List.of("LIKE", "LOVE", "CARE", "HAHA", "WOW", "SAD", "ANGRY"))
            result.add("reaction:" + reaction);
          if (t.metadata().path("_photo").asBoolean()) return result;
          add(result, "insight:", s.getFacebookPostMetrics());
          for (String video : videoIds(t.metadata()))
            for (String metric : s.getFacebookVideoMetrics()) {
              validate(metric);
              result.add("video:" + video + ":" + metric);
            }
        }
      }
      case "linkedin" -> {
        if (!a.externalAccountId().startsWith("urn:li:organization:")) return List.of();
        if (t.account()) result.addAll(List.of("shares", "followers", "page"));
        else if (t.externalId().startsWith("urn:li:share:") || t.externalId().startsWith("urn:li:ugcPost:"))
          result.add("shares");
      }
      default -> { }
    }
    if (a.platform().equals("instagram") || a.platform().equals("facebook"))
      s.getInsightReports().forEach((name, report) -> {
        if (report.getPlatform().equals(a.platform()) && report.getScope().equals(t.account() ? "account" : "content")) {
          validate(name.replace('-', '_')); validate(report.getMetric()); result.add("custom:" + name);
        }
      });
    return result.stream().distinct().toList();
  }

  private void add(List<String> result, String prefix, List<String> metrics) {
    for (var metric : metrics) { validate(metric); result.add(prefix + metric); }
  }

  private void validate(String metric) {
    if (metric == null || !metric.matches("[A-Za-z][A-Za-z0-9_]{0,99}"))
      throw new IllegalArgumentException("Invalid statistics metric name");
  }

  private Set<String> videoIds(JsonNode metadata) {
    var ids = new LinkedHashSet<String>();
    collectVideos(metadata.path("attachments"), ids);
    return ids;
  }

  private void collectVideos(JsonNode attachments, Set<String> ids) {
    if (!attachments.isArray()) return;
    for (var node : attachments) {
      String type = node.path("type").asText("").toLowerCase(Locale.ROOT);
      String id = node.path("target").path("id").asText("");
      if ((type.contains("video") || type.contains("reel")) && id.matches("[0-9]+")) ids.add(id);
      collectVideos(node.path("subattachments").path("data"), ids);
    }
  }

  public Page fetch(SocialAccount a, Target t, String operation, String cursor, LocalDate from, LocalDate to) {
    if (!operations(a, t).contains(operation))
      throw new IllegalArgumentException("Statistics operation no longer configured");
    return switch (a.platform()) {
      case "youtube" -> youtube(a, t, operation, cursor, from, to);
      case "instagram", "facebook" -> graph(a, t, operation, cursor, from, to);
      case "linkedin" -> linkedin(a, t, operation, cursor);
      default -> throw new IllegalArgumentException("Unsupported statistics platform");
    };
  }

  private Page youtube(SocialAccount a, Target t, String op, String cursor, LocalDate from, LocalDate to) {
    JsonNode response;
    if (op.equals("public")) {
      var cfg = properties.getPlatforms().get("youtube").getAccounts().stream()
          .filter(c -> c.getExternalId().equals(a.externalAccountId())).findFirst().orElseThrow();
      String token = credentials.optional(a.credentialReference());
      if (a.credentialExpiresAt()!=null && !a.credentialExpiresAt().isAfter(java.time.Instant.now())) token = null;
      String key = credentials.optional(cfg.getApiKeyReference());
      if (token == null && key == null) throw new AccessUnavailable();
      response = http.get("youtube", t.account() ? "channels" : "videos",
          Map.of("part", "statistics", "id", t.externalId()), token, key);
      if (!response.path("items").isArray()) throw new IllegalArgumentException("Missing YouTube items");
      return new Page(response, null);
    }
    String token = credentials.optional(a.credentialReference());
    if (token == null) throw new AccessUnavailable();
    var q = new HashMap<String, String>();
    q.put("ids", "channel==" + a.externalAccountId());
    q.put("startDate", from.toString()); q.put("endDate", to.toString());
    if (op.startsWith("report:")) {
      var report = properties.getStatistics().getYoutubeReports().get(op.substring(7));
      q.put("metrics", report.getMetrics()); q.put("dimensions", report.getDimensions());
    } else {
      q.put("metrics", op.substring(10)); q.put("dimensions", "day"); q.put("sort", "day");
    }
    q.put("maxResults", "200");
    int start = cursor == null ? 1 : Integer.parseInt(cursor);
    if (start < 1) throw new IllegalArgumentException("Invalid report cursor");
    q.put("startIndex", String.valueOf(start));
    if (!t.account()) q.put("filters", "video==" + t.externalId());
    response = http.youtubeAnalytics(q, token);
    if (!response.path("columnHeaders").isArray() || (response.has("rows") && !response.path("rows").isArray()))
      throw new IllegalArgumentException("Invalid YouTube report");
    return new Page(response, response.path("rows").size() == 200 ? String.valueOf(start + 200) : null);
  }

  private Page graph(SocialAccount a, Target t, String op, String cursor, LocalDate from, LocalDate to) {
    String path = t.externalId();
    var q = new HashMap<String, String>();
    if (op.equals("public")) {
      q.put("fields", a.platform().equals("instagram")
          ? (t.account() ? "id,followers_count,follows_count,media_count" : "id,like_count,comments_count")
          : "id,reactions.limit(0).summary(true),comments.limit(0).summary(true),shares");
    } else if (op.startsWith("field:")) q.put("fields", "id," + op.substring(6));
    else if (op.startsWith("reaction:")) q.put("fields", "id,reactions.type(" + op.substring(9) + ").limit(0).summary(true)");
    else {
      String metric;
      if (op.startsWith("custom:")) {
        var report = properties.getStatistics().getInsightReports().get(op.substring(7));
        path += "/insights"; metric = report.getMetric();
      } else if (op.startsWith("video:")) {
        String[] parts = op.split(":");
        path = parts[1] + "/video_insights"; metric = parts[2];
      } else { path += "/insights"; metric = op.substring(8); }
      q.put("metric", metric);
      if (t.account()) {
        q.put("period", "day");
        q.put("since", String.valueOf(from.atStartOfDay(ZoneOffset.UTC).toEpochSecond()));
        q.put("until", String.valueOf(to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()));
        if (a.platform().equals("instagram")) q.put("metric_type", "total_value");
      }
      if (op.startsWith("custom:")) {
        var report = properties.getStatistics().getInsightReports().get(op.substring(7));
        if (report.getPeriod()!=null) q.put("period",report.getPeriod());
        if (report.getMetricType()!=null) q.put("metric_type",report.getMetricType());
        if (report.getBreakdown()!=null) q.put("breakdown",report.getBreakdown());
        if (report.getTimeframe()!=null) {
          q.remove("since"); q.remove("until"); q.put("timeframe",report.getTimeframe());
        }
      }
      q.put("limit", "100"); applyCursor(q, cursor);
    }
    JsonNode response = get(a, path, q);
    boolean edge = !op.equals("public") && !op.startsWith("field:") && !op.startsWith("reaction:");
    if (edge && !response.path("data").isArray()) throw new IllegalArgumentException("Missing insight data");
    if (!edge && !response.hasNonNull("id")) throw new IllegalArgumentException("Missing object id");
    String next = edge ? next(response) : null;
    return new Page(sanitize(response), next);
  }

  private Page linkedin(SocialAccount a, Target t, String op, String cursor) {
    String version = properties.getPlatforms().get("linkedin").getApiVersion();
    if (!version.matches("[0-9]{6}")) throw new IllegalArgumentException("LinkedIn requires YYYYMM version");
    String endpoint = switch (op) {
      case "shares" -> "organizationalEntityShareStatistics";
      case "followers" -> "organizationalEntityFollowerStatistics";
      case "page" -> "organizationPageStatistics";
      default -> throw new IllegalArgumentException("Invalid LinkedIn operation");
    };
    String field = op.equals("page") ? "organization" : "organizationalEntity";
    var q = new HashMap<>(Map.of("q", field, field, a.externalAccountId()));
    if (!t.account()) q.put(t.externalId().startsWith("urn:li:share:") ? "shares" : "ugcPosts",
        "List(" + t.externalId() + ")");
    if (cursor != null) q.put("start", cursor);
    JsonNode response = http.get("linkedin", endpoint, q, credentials.required(a.credentialReference()), null,
        Map.of("Linkedin-Version", version, "X-Restli-Protocol-Version", "2.0.0"));
    if (!response.path("elements").isArray()) throw new IllegalArgumentException("Missing LinkedIn elements");
    String next = null;
    for (var link : response.path("paging").path("links"))
      if (link.path("rel").asText().equals("next")) {
        var query = java.net.URI.create(link.path("href").asText()).getRawQuery();
        if (query != null) for (var part : query.split("&")) {
          var pair = part.split("=", 2);
          if (pair.length == 2 && pair[0].equals("start") && pair[1].matches("[0-9]+")) next = pair[1];
        }
        if (next == null) throw new IllegalArgumentException("Invalid LinkedIn paging");
      }
    return new Page(sanitize(response), next);
  }

  // Provider pagination URLs may contain access tokens; persist only safe cursors separately.
  private JsonNode sanitize(JsonNode node) {
    JsonNode copy = node.deepCopy();
    scrub(copy);
    return copy;
  }
  private void scrub(JsonNode node) {
    if (node instanceof ObjectNode object) {
      object.remove(List.of("paging", "access_token", "previous", "next"));
      object.elements().forEachRemaining(this::scrub);
    } else if (node.isArray()) node.forEach(this::scrub);
  }
  public static class AccessUnavailable extends RuntimeException {}
}
