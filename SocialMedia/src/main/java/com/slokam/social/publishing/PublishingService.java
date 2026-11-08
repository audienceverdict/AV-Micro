package com.slokam.social.publishing;

import com.slokam.social.configuration.SocialProperties;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PublishingService {
  public enum ContentType { TEXT, IMAGE, VIDEO }
  public record CreateContent(
      @Schema(description = "Publication title", example = "Product launch") @NotBlank @Size(max = 100) String title,
      @Schema(description = "Caption; nonblank for TEXT", example = "Meet our latest release") @NotNull @Size(max = 10000) String caption,
      @Schema(description = "TEXT: no file; IMAGE: JPEG/PNG; VIDEO: MP4", example = "VIDEO") @NotNull ContentType type,
      @Schema(description = "Save without queueing destination jobs", defaultValue = "false") boolean draft,
      @Schema(description = "YouTube visibility; defaults to public when omitted", allowableValues = {"public", "private", "unlisted"}) @Pattern(regexp = "public|private|unlisted") String youtubePrivacy,
      @NotNull com.slokam.social.moderation.PostCategory category) {}
  private static final Set<String> PLATFORMS = Set.of("youtube", "facebook", "instagram");
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final SocialProperties accounts;
  private final PublishingProperties properties;
  private final PublishingMediaStore media;

  public PublishingService(JdbcTemplate jdbc, TransactionTemplate transactions, SocialProperties accounts,
      PublishingProperties properties, PublishingMediaStore media) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.accounts = accounts;
    this.properties = properties;
    this.media = media;
  }

  public Map<String, Object> create(String key, CreateContent request, MultipartFile file) {
    if (key == null || !key.matches("[A-Za-z0-9_.:-]{1,128}"))
      throw badRequest("Idempotency-Key must contain 1-128 letters, digits, dots, colons, underscores or hyphens");
    var previous = jdbc.queryForList("SELECT id FROM application_content WHERE request_key=?", String.class, key);
    // The first accepted payload wins for a given key. Do not re-upload media or enqueue twice.
    if (!previous.isEmpty()) return get(previous.getFirst());
    if (request.category() == null) throw badRequest("Choose MOVIE or AV and supply its name");
    request.category().validate();
    String mime = validateFile(request, file);
    String id = UUID.randomUUID().toString();
    if (file != null && !file.isEmpty()) media.save(id, file);
    try {
      transactions.executeWithoutResult(tx -> {
        jdbc.update("INSERT INTO application_content(id,request_key,title,caption,content_type,media_type,media_size,youtube_privacy,topic,category_name,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",
            id, key, request.title(), request.caption(), request.type().name(), mime,
            file == null ? 0L : file.getSize(), request.youtubePrivacy() == null ? "public" : request.youtubePrivacy(),
            request.category().topic().name(), request.category().name());
        if (!request.draft()) enqueue(id);
      });
    } catch (DuplicateKeyException e) {
      media.delete(id);
      var winner = jdbc.queryForList("SELECT id FROM application_content WHERE request_key=?", String.class, key);
      if (!winner.isEmpty()) return get(winner.getFirst());
      throw e;
    } catch (RuntimeException e) {
      media.delete(id);
      throw e;
    }
    return get(id);
  }

  private String validateFile(CreateContent request, MultipartFile file) {
    boolean present = file != null && !file.isEmpty();
    if (request.type() == ContentType.TEXT) {
      if (present) throw badRequest("Text content must not contain a media file");
      if (request.caption().isBlank()) throw badRequest("Text content requires a caption");
      return null;
    }
    if (!present) throw badRequest("A media file is required");
    if (file.getSize() > properties.getMaxMediaBytes())
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Media exceeds configured limit");
    String mime = file.getContentType();
    if (request.type() == ContentType.IMAGE && !Set.of("image/jpeg", "image/png").contains(mime == null ? "" : mime))
      throw badRequest("Images must be JPEG or PNG; Instagram publishing requires JPEG");
    if (request.type() == ContentType.VIDEO && !"video/mp4".equals(mime))
      throw badRequest("Videos must be MP4");
    return mime;
  }

  public Map<String, Object> get(String id) {
    var rows = jdbc.queryForList("SELECT id,title,caption,content_type,media_type,media_size,youtube_privacy,topic,category_name,created_at FROM application_content WHERE id=?", id);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found");
    var result = new LinkedHashMap<>(rows.getFirst());
    result.put("publications", jdbc.queryForList("SELECT id,platform,external_account_id,status,external_content_id,permalink,error_message,attempts,updated_at FROM social_publications WHERE content_id=? ORDER BY platform,external_account_id", id));
    return result;
  }

  public Map<String, Object> publish(String id) {
    transactions.executeWithoutResult(tx -> {
      if (jdbc.queryForList("SELECT id FROM application_content WHERE id=? FOR UPDATE", id).isEmpty())
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found");
      // Snapshot accounts once; repeated requests cannot add new destinations to this publication.
      if (jdbc.queryForObject("SELECT COUNT(*) FROM social_publications WHERE content_id=?", Long.class, id) == 0)
        enqueue(id);
    });
    return get(id);
  }

  private void enqueue(String id) {
    if (!properties.isEnabled())
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Publishing is disabled; save as a draft");
    int count = 0;
    for (var platform : accounts.getPlatforms().entrySet()) {
      if (!PLATFORMS.contains(platform.getKey()) || !platform.getValue().isEnabled()) continue;
      for (var account : platform.getValue().getAccounts()) {
        if (!account.isEnabled() || !account.isPublishingEnabled()) continue;
        jdbc.update("INSERT INTO social_publications(id,content_id,platform,external_account_id,status,next_attempt_at,deadline_at,created_at,updated_at) VALUES(?,?,?,?,'QUEUED',UTC_TIMESTAMP(6),DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 24 HOUR),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE id=id",
            UUID.randomUUID().toString(), id, platform.getKey(), account.getExternalId());
        count++;
      }
    }
    if (count == 0) throw badRequest("No enabled publishing accounts are configured");
  }

  public void retry(String id) {
    int changed = jdbc.update("UPDATE social_publications SET status=COALESCE(resume_status,'QUEUED'),error_message=NULL,next_attempt_at=UTC_TIMESTAMP(6),deadline_at=DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 24 HOUR),updated_at=UTC_TIMESTAMP(6) WHERE id=? AND status IN ('FAILED','ACTION_REQUIRED')", id);
    if (changed == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only explicitly failed or action-required publications can be retried; reconcile UNKNOWN results with the provider first");
  }

  public Map<String, Object> mediaMetadata(String id) {
    var result = get(id);
    if (((Number) result.get("media_size")).longValue() == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content has no media");
    return result;
  }

  private ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
