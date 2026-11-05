package com.slokam.social.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slokam.social.core.model.*;
import com.slokam.social.core.spi.*;
import java.util.*;

/** Template: fetch profile, read a page, enrich each composite, map, then emit continuation. */
public abstract class AbstractGraphAdapter implements SocialPlatformAdapter {
  protected abstract JsonNode profile(SocialAccount account);

  protected abstract JsonNode page(SocialAccount account, ContentQuery query);

  protected abstract ObjectNode enrich(
      SocialAccount account, ContentQuery query, JsonNode raw, JsonNode profile);

  protected abstract String next(SocialAccount account, ContentQuery query, JsonNode page);

  protected abstract ContentMapper mapper();

  public final PageResult<SocialContent> fetchContent(SocialAccount a, ContentQuery q) {
    var profile = profile(a);
    var page = page(a, q);
    var records = new ArrayList<SocialContent>();
    for (var raw : JsonSupport.array(page, "data")) {
      var content = mapper().map(a, enrich(a, q, raw, profile));
      if (q.requestedTypes().isEmpty() || q.requestedTypes().contains(content.contentType()))
        records.add(content);
    }
    String next = next(a, q, page);
    return new PageResult<>(records, next, next != null, Map.of("platform", platformId()));
  }
}
