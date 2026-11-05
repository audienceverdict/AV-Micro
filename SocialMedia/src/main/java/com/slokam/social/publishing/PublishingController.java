package com.slokam.social.publishing;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Publishing")
@RestController
@RequestMapping("/api")
public class PublishingController {
  private final PublishingService service;
  private final PublishingMediaStore media;
  public PublishingController(PublishingService service, PublishingMediaStore media) {
    this.service = service;
    this.media = media;
  }

  @Operation(summary = "Save content and queue publication", description = "Send multipart/form-data with an application/json metadata part and optional binary file. TEXT requires a nonblank caption and no file; IMAGE requires JPEG or PNG; VIDEO requires MP4. Instagram images require JPEG. draft=true saves without queueing. Otherwise enabled YouTube, Facebook and Instagram accounts are snapshotted into durable jobs. No provider calls occur in this request. Reusing an Idempotency-Key returns the first saved payload.")
  @PostMapping(value = "/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@Parameter(description = "Required request deduplication key. Publishing accepts 1-128 letters, digits, dots, colons, underscores or hyphens; reply keys are scoped to the operator.", example = "campaign-2026-001") @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestPart("metadata") PublishingService.CreateContent metadata,
      @RequestPart(value = "file", required = false) MultipartFile file) {
    return service.create(key, metadata, file);
  }

  @Operation(summary = "Get content and publication results", description = "Returns content and per-destination jobs, statuses, external IDs, links, attempts and safe errors. PUBLISHED reflects provider processing; YouTube visibility still depends on privacy and project restrictions.")
  @GetMapping("/content/{id}")
  public Map<String, Object> get(@PathVariable String id) { return service.get(id); }

  @Operation(summary = "Queue a saved draft", description = "Snapshots enabled destinations once. Repeated requests do not republish or add new destinations. Returns 503 if publishing is disabled and 400 if no enabled destinations exist.")
  @PostMapping("/content/{id}/publish")
  public Map<String, Object> publish(@PathVariable String id) { return service.publish(id); }

  @Operation(summary = "Retry a failed publication", description = "Only FAILED or ACTION_REQUIRED jobs may resume. Returns 409 for other states, including UNKNOWN. Inspect ambiguous deliveries on the provider before recovery. Grants a new 24-hour processing window.")
  @PostMapping("/publications/{id}/retry")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void retry(@PathVariable String id) { service.retry(id); }

  // Expose ONLY this opaque media route through a public HTTPS reverse proxy for Meta fetches.
  // Spring MVC Resource responses support byte-range requests for videos.
  @Operation(summary = "Download saved publication media", description = "Binary media for provider fetches; supports HTTP byte ranges. Content-Type and length reflect the saved file. Returns 404 for missing content or content without media. Only this opaque media route is intended for public HTTPS proxy exposure.")
  @GetMapping("/content/{id}/media")
  public ResponseEntity<Resource> media(@PathVariable String id) {
    var metadata = service.mediaMetadata(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType((String) metadata.get("media_type")))
        .contentLength(((Number) metadata.get("media_size")).longValue())
        .header("X-Content-Type-Options", "nosniff")
        .cacheControl(CacheControl.noStore())
        .body(media.resource(id));
  }
}
