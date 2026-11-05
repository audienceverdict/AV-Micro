package com.slokam.social.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.slokam.social.configuration.SocialProperties;
import com.slokam.social.core.model.SocialAccount;
import com.slokam.social.infrastructure.credentials.CredentialResolver;
import com.slokam.social.infrastructure.resilience.ProviderHttpClient;
import java.util.*;

public abstract class GraphApiClient {
  protected final ProviderHttpClient http;
  protected final CredentialResolver credentials;
  protected final SocialProperties properties;

  protected GraphApiClient(ProviderHttpClient h, CredentialResolver c, SocialProperties p) {
    http = h;
    credentials = c;
    properties = p;
  }

  protected JsonNode get(SocialAccount a, String path, Map<String, String> query) {
    return http.get(
        a.platform(),
        properties.getPlatforms().get(a.platform()).getApiVersion() + "/" + path,
        query,
        credentials.required(a.credentialReference()),
        null);
  }

  public JsonNode objectEdge(
      SocialAccount a, String id, String edge, String fields, String cursor, int size) {
    var q = new HashMap<String, String>();
    q.put("fields", fields);
    q.put("limit", String.valueOf(Math.min(size, 100)));
    if (a.platform().equals("facebook") && edge.equals("comments")) q.put("filter", "toplevel");
    applyCursor(q, cursor);
    return get(a, id + "/" + edge, q);
  }

  public JsonNode profile(SocialAccount a, String fields) {
    return get(a, a.externalAccountId(), Map.of("fields", fields));
  }

  public JsonNode edge(SocialAccount a, String edge, String fields, String cursor, int size) {
    return edge(a, edge, fields, cursor, size, Map.of());
  }

  public JsonNode edge(
      SocialAccount a,
      String edge,
      String fields,
      String cursor,
      int size,
      Map<String, String> bounds) {
    var q = new HashMap<>(bounds);
    q.put("fields", fields);
    q.put("limit", String.valueOf(Math.min(size, 100)));
    applyCursor(q, cursor);
    return get(a, a.externalAccountId() + "/" + edge, q);
  }

  private static final Set<String> PAGING =
      Set.of("after", "before", "since", "until", "__paging_token");

  protected void applyCursor(Map<String, String> query, String cursor) {
    if (cursor == null) return;
    if (!cursor.startsWith("paging:")) {
      query.put("after", cursor);
      return;
    }
    try {
      String decoded =
          new String(
              Base64.getUrlDecoder().decode(cursor.substring(7)),
              java.nio.charset.StandardCharsets.UTF_8);
      for (String part : decoded.split("&")) {
        String[] pair = part.split("=", 2);
        if (pair.length != 2 || !PAGING.contains(pair[0]))
          throw new IllegalArgumentException("Invalid paging parameter");
        query.put(
            pair[0], java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8));
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid Graph continuation");
    }
  }

  public String next(JsonNode page) {
    String url = JsonSupport.text(page.path("paging"), "next");
    if (url == null) return null;
    String after = JsonSupport.text(page.path("paging").path("cursors"), "after");
    if (after != null) return after;
    var parameters = new TreeMap<String, String>();
    try {
      String raw = java.net.URI.create(url).getRawQuery();
      if (raw != null)
        for (String part : raw.split("&")) {
          String[] pair = part.split("=", 2);
          if (pair.length == 2 && PAGING.contains(pair[0])) parameters.put(pair[0], pair[1]);
        }
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid Graph paging response");
    }
    if (parameters.isEmpty())
      throw new IllegalArgumentException("Graph response has no continuation");
    String raw =
        parameters.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(java.util.stream.Collectors.joining("&"));
    return "paging:"
        + Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  protected JsonNode allChildren(SocialAccount a, String id, String edge, String fields) {
    var result = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
    String cursor = null;
    var seen = new HashSet<String>();
    do {
      var q = new HashMap<>(Map.of("fields", fields, "limit", "100"));
      applyCursor(q, cursor);
      var page = get(a, id + "/" + edge, q);
      JsonSupport.array(page, "data").forEach(result::add);
      cursor = next(page);
      if (cursor != null && !seen.add(cursor))
        throw new IllegalArgumentException("Repeated child cursor");
      if (seen.size() > 100) throw new IllegalArgumentException("Excessive child pagination");
    } while (cursor != null);
    return result;
  }
}
