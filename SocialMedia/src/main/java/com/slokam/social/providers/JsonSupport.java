package com.slokam.social.providers;

import com.fasterxml.jackson.databind.*;
import com.slokam.social.core.model.*;
import java.time.*;
import java.util.*;

public final class JsonSupport {
  private JsonSupport() {}

  public static String text(JsonNode n, String field) {
    var v = n.path(field);
    return v.isMissingNode() || v.isNull() ? null : v.asText();
  }

  public static Instant instant(String s) {
    if (s == null) return null;
    try {
      return Instant.parse(s);
    } catch (java.time.format.DateTimeParseException e) {
      return OffsetDateTime.parse(
              s, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"))
          .toInstant();
    }
  }

  public static List<JsonNode> array(JsonNode root, String field) {
    var n = root.path(field);
    if (!n.isArray()) throw new IllegalArgumentException("Provider response missing " + field);
    var out = new ArrayList<JsonNode>();
    n.forEach(out::add);
    return out;
  }

  public static Integer integer(JsonNode n, String field) {
    return n.hasNonNull(field) ? n.path(field).asInt() : null;
  }

  public static String thumbnail(JsonNode n) {
    return text(n.path("snippet").path("thumbnails").path("high"), "url");
  }

  public static SocialContent content(
      SocialAccount a,
      JsonNode n,
      ContentType type,
      String providerType,
      String authorId,
      String authorName,
      String title,
      String caption,
      String url,
      String thumb,
      Instant published,
      Instant updated,
      JsonNode engagement,
      List<MediaAsset> assets) {
    return new SocialContent(
        null,
        a.platform(),
        text(n, "id"),
        a.id(),
        authorId,
        authorName,
        type,
        providerType,
        title,
        caption,
        url,
        thumb,
        published,
        updated,
        Instant.now(),
        engagement,
        n,
        ContentStatus.AVAILABLE,
        assets);
  }
}
