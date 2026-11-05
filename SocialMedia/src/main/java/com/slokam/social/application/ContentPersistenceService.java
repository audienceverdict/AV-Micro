package com.slokam.social.application;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.slokam.social.core.model.*;
import com.slokam.social.infrastructure.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentPersistenceService {
  private final ContentRepository content;
  private final AssetRepository assets;
  private final ObjectMapper mapper;

  public ContentPersistenceService(ContentRepository c, AssetRepository a, ObjectMapper m) {
    content = c;
    assets = a;
    mapper = m;
  }

  public record Counts(long inserted, long updated, long skipped) {
    public Counts plus(Counts c) {
      return new Counts(inserted + c.inserted, updated + c.updated, skipped + c.skipped);
    }
  }

  @Transactional
  public Counts persist(List<SocialContent> records) {
    long inserted = 0, updated = 0, skipped = 0;
    for (var c : records) {
      var found =
          content.findByPlatformAndAccountIdAndExternalContentId(
              c.platform(), c.accountId(), c.externalContentId());
      var e = found.orElseGet(ContentEntity::new);
      // Absent metrics mean inaccessible, not zero. Preserve previously retrieved metrics.
      JsonNode metrics =
          c.engagementMetadata() == null ? e.engagementMetadata : c.engagementMetadata();
      String fingerprint = fingerprint(c, metrics);
      if (found.isPresent() && fingerprint.equals(e.fingerprint)) {
        e.retrievedAt = c.retrievedAt();
        skipped++;
        continue;
      }
      e.accountId = c.accountId();
      e.platform = c.platform();
      e.externalContentId = c.externalContentId();
      e.contentType = c.contentType();
      e.providerContentType = c.providerContentType();
      e.authorId = c.authorId();
      e.authorName = c.authorName();
      e.title = c.title();
      e.caption = c.caption();
      e.permalink = c.permalink();
      e.thumbnailUrl = c.thumbnailUrl();
      e.publishedAt = c.publishedAt();
      e.providerUpdatedAt = c.providerUpdatedAt();
      e.retrievedAt = c.retrievedAt();
      e.engagementMetadata = metrics;
      e.providerMetadata = c.providerMetadata();
      e.contentStatus = c.status();
      e.fingerprint = fingerprint;
      e = content.saveAndFlush(e);
      reconcile(e.id, c.mediaAssets());
      if (found.isEmpty()) inserted++;
      else updated++;
    }
    return new Counts(inserted, updated, skipped);
  }

  private String fingerprint(SocialContent c, JsonNode metrics) {
    try {
      ObjectNode node = mapper.valueToTree(c);
      node.remove(List.of("id", "retrievedAt"));
      node.set("engagementMetadata", metrics);
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical(node).toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private JsonNode canonical(JsonNode n) {
    if (n.isObject()) {
      var out = mapper.createObjectNode();
      var keys = new TreeSet<String>();
      n.fieldNames().forEachRemaining(keys::add);
      keys.forEach(k -> out.set(k, canonical(n.get(k))));
      return out;
    }
    if (n.isArray()) {
      var out = mapper.createArrayNode();
      n.forEach(v -> out.add(canonical(v)));
      return out;
    }
    return n;
  }

  private void reconcile(Long id, List<MediaAsset> incoming) {
    var existing = new HashMap<String, AssetEntity>();
    assets.findByContentId(id).forEach(a -> existing.put(a.externalAssetId, a));
    for (var a : incoming) {
      var e = existing.remove(a.externalAssetId());
      if (e == null) e = new AssetEntity();
      e.contentId = id;
      e.externalAssetId = a.externalAssetId();
      e.mediaType = a.mediaType();
      e.mediaUrl = a.mediaUrl();
      e.thumbnailUrl = a.thumbnailUrl();
      e.width = a.width();
      e.height = a.height();
      e.durationSeconds = a.durationSeconds();
      e.displayOrder = a.displayOrder();
      assets.save(e);
    }
    assets.deleteAll(existing.values());
  }
}
