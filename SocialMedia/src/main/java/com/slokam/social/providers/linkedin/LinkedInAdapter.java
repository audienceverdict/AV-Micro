package com.slokam.social.providers.linkedin;

import static com.slokam.social.providers.JsonSupport.*;

import com.slokam.social.core.capability.PlatformCapabilities;
import com.slokam.social.core.model.*;
import com.slokam.social.core.spi.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "social.platforms.linkedin.enabled",
    havingValue = "true")
public class LinkedInAdapter implements SocialPlatformAdapter {
  private final LinkedInApiClient client;
  private final ContentMapper mapper;

  public LinkedInAdapter(LinkedInApiClient c, @Qualifier("linkedInMapper") ContentMapper m) {
    client = c;
    mapper = m;
  }

  public String platformId() {
    return "linkedin";
  }

  public PlatformCapabilities capabilities() {
    return new PlatformCapabilities(
        Set.of(ContentType.TEXT, ContentType.UNKNOWN), false, true, false);
  }

  public PageResult<SocialContent> fetchContent(SocialAccount a, ContentQuery q) {
    var response = client.posts(a, q.cursor(), q.pageSize());
    String next = client.next(response);
    var records =
        array(response, "elements").stream()
            .map(n -> mapper.map(a, n))
            .filter(
                c -> q.requestedTypes().isEmpty() || q.requestedTypes().contains(c.contentType()))
            .toList();
    return new PageResult<>(
        records, next, next != null, Map.of("operation", "posts author finder"));
  }
}
